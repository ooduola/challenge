package challenge

import cats.effect.IO
import challenge.model.invoices.Invoice.Invoice
import challenge.model.invoices.InvoiceId.InvoiceId
import challenge.model.invoices.NewInvoice.NewInvoice
import challenge.utils.DateTimeUtils._
import doobie.ConnectionIO
import doobie.implicits._
import doobie.implicits.javatime._
import doobie.util.transactor.Transactor
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

import java.time.LocalDateTime


trait Invoices[F[_]] {
  def get(id: InvoiceId): F[Option[Invoice]]
  def create(invoice: NewInvoice): F[InvoiceId]
}

object Invoices {

  def impl(tx: Transactor[IO]): Invoices[IO] = new Invoices[IO] {
    override def get(invoiceId: InvoiceId): IO[Option[Invoice]] = {
      sql"""SELECT invoiceId, total, payerId, sentAt FROM invoice WHERE invoiceId = ${invoiceId.id}"""
        .query[Invoice]
        .to[List]
        .transact(tx)
        .map(_.headOption)
    }

    override def create(newInvoice: NewInvoice): IO[InvoiceId] = {
      val sentAt = newInvoice.sentAt
        .map(toUtc)
        .getOrElse(toUtc(LocalDateTime.now()))

      val createInvoiceQuery = for {
        invoiceId <- insertInvoice(newInvoice, sentAt)
        previousBalance <- getPreviousBalance(newInvoice.payerId, sentAt)
        newBalance = previousBalance.getOrElse(0.0) + newInvoice.total
        _ <- updateBalance(newInvoice.payerId, sentAt, newBalance)
      } yield InvoiceId(invoiceId)

      createInvoiceQuery.transact(tx)
    }

    private def insertInvoice(newInvoice: NewInvoice, sentAt: LocalDateTime): ConnectionIO[Int] =
      sql"""
           |INSERT INTO invoice (total, payerId, sentAt)
           |VALUES (${newInvoice.total}, ${newInvoice.payerId}, $sentAt)
     """.stripMargin.update.withUniqueGeneratedKeys[Int]("invoiceId")

    private def getPreviousBalance(payerId: Int, sentAt: LocalDateTime): ConnectionIO[Option[Double]] =
      sql"""
           |SELECT balance
           |FROM balance
           |WHERE payerId = ${payerId} AND balanceDate < $sentAt
           |ORDER BY balanceDate DESC
           |LIMIT 1
        """.stripMargin.query[Double].option

    private def updateBalance(payerId: Int, sentAt: LocalDateTime, balance: Double): ConnectionIO[Int] =
      sql"""
           |INSERT INTO balance (payerId, balanceDate, balance)
           |VALUES ($payerId, $sentAt, $balance)
           |ON DUPLICATE KEY UPDATE balance = VALUES(balance)
        """.stripMargin.update.run
  }

  def routes(invoices: Invoices[IO]): HttpRoutes[IO] = {
    val dsl = new Http4sDsl[IO] {}
    import dsl._

    HttpRoutes.of[IO] {
      case GET -> Root / "invoice" / IntVar(invoiceId) =>
        invoices.get(InvoiceId(invoiceId)).flatMap {
          case Some(invoice) => Ok(invoice)
          case None          => NotFound()
        }

      case req @ POST -> Root / "invoice" =>
        req.decode[NewInvoice] { input =>
          invoices.create(input).flatMap(Ok(_))
        }
    }
  }
}
