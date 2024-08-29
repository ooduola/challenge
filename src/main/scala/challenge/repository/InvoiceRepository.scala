package challenge.repository

import cats.effect.IO
import challenge.model.invoices.Invoice.Invoice
import challenge.model.invoices.InvoiceId.InvoiceId
import challenge.model.invoices.NewInvoice.NewInvoice
import challenge.model.payments.Payment.Payment
import challenge.utils.DateTimeUtils.toUtc
import doobie.ConnectionIO
import doobie.implicits._
import doobie.implicits.javatime._
import doobie.util.transactor.Transactor

import java.time.LocalDateTime

trait InvoiceRepository[F[_]] {
  def get(paymentId: InvoiceId): F[Option[Invoice]]
  def create(newPayment: NewInvoice): F[InvoiceId]
  def insertInvoice(newPayment: NewInvoice, sentAt: LocalDateTime): ConnectionIO[Int]
  def getUnpaidInvoicesByPayer(payerId: Int): F[List[Invoice]]
  def getInvoicesByPayment(paymentId: Int): F[List[Invoice]]
  def getPaymentsByInvoice(invoiceId: Int): F[List[Payment]]
}

class InvoiceRepositoryImpl(implicit tx: Transactor[IO]) extends InvoiceRepository[IO] {

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

  override def insertInvoice(newInvoice: NewInvoice, sentAt: LocalDateTime): ConnectionIO[Int] =
    sql"""
         |INSERT INTO invoice (total, payerId, sentAt)
         |VALUES (${newInvoice.total}, ${newInvoice.payerId}, $sentAt)
    """.stripMargin.update.withUniqueGeneratedKeys[Int]("invoiceId")

  override def getUnpaidInvoicesByPayer(payerId: Int): IO[List[Invoice]] =
    sql"""
         |SELECT * FROM invoice
         |WHERE payerId = $payerId
         |AND invoiceId NOT IN (SELECT invoiceId FROM `payment_invoice`)
         |ORDER BY sentAt ASC;
    """.stripMargin.query[Invoice]
      .to[List]
      .transact(tx)

  override def getInvoicesByPayment(paymentId: Int): IO[List[Invoice]] = {
    sql"""
      SELECT i.*
      FROM invoice i
      JOIN payment_invoice pi ON i.invoiceId = pi.invoiceId
      WHERE pi.paymentId = $paymentId
    """.query[Invoice]
      .to[List]
      .transact(tx)
  }

  override def getPaymentsByInvoice(invoiceId: Int): IO[List[Payment]] = {
    sql"""
      SELECT p.*
      FROM payment p
      JOIN payment_invoice pi ON p.paymentId = pi.paymentId
      WHERE pi.invoiceId = $invoiceId
    """.query[Payment]
      .to[List]
      .transact(tx)
  }

  private def getPreviousBalance(payerId: Int, sentAt: LocalDateTime): ConnectionIO[Option[Double]] =
    sql"""
         |SELECT balance
         |FROM balance
         |WHERE payerId = $payerId AND balanceDate < $sentAt
         |ORDER BY balanceDate DESC
         |LIMIT 1
    """.stripMargin.query[Double].option

  private def updateBalance(payerId: Int, balanceDate: LocalDateTime, balance: Double): ConnectionIO[Int] =
    sql"""
         |INSERT INTO balance (payerId, balanceDate, balance)
         |VALUES ($payerId, $balanceDate, $balance)
         |ON DUPLICATE KEY UPDATE balance = VALUES(balance)
    """.stripMargin.update.run

}
