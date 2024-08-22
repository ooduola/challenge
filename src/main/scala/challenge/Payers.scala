package challenge

import java.time.{LocalDate, ZoneOffset}
import cats.Applicative
import cats.effect.{IO, Sync}
import challenge.model.payers.PayerId.PayerId
import challenge.model.invoices.Invoice.Invoice
import challenge.model.payers.Balance.Balance
import challenge.model.payers.NewPayer.NewPayer
import challenge.model.payers.Payers.Payer
import challenge.model.payments.Payment.Payment
import doobie.implicits._
import doobie.implicits.javatime._
import doobie.util.transactor.Transactor
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.dsl.Http4sDsl
import org.http4s.{EntityDecoder, EntityEncoder, HttpRoutes}

trait Payers[F[_]] {
  def get(id: PayerId): F[Option[Payer]]
  def create(payer: NewPayer): F[PayerId]
  def balance(payerId: PayerId, date: LocalDate): F[Balance]
}

object Payers {

  def impl(tx: Transactor[IO]): Payers[IO] = new Payers[IO] {
    override def get(payerId: PayerId): IO[Option[Payer]] = {
      sql"""SELECT payerId, name FROM payer WHERE payerId = ${payerId.id}"""
        .query[Payer]
        .to[List]
        .transact(tx)
        .map(_.headOption)
    }

    override def create(newPayer: NewPayer): IO[PayerId] = {
      val q = for {
        id <- {
          sql"""INSERT INTO payer (name)
               |VALUES (${newPayer.name})
             """.stripMargin.update.withUniqueGeneratedKeys[Int]("payerId")
        }
      } yield PayerId(id)

      q.transact(tx)
    }

    override def balance(payerId: PayerId, date: LocalDate): IO[Balance] = {
      val invoicesIO = {
        sql"""SELECT invoiceId, total, payerId, sentAt FROM invoice
             |WHERE payerId = ${payerId.id}
           """.stripMargin
          .query[Invoice]
          .to[List]
          .transact(tx)
      }

      val paymentsIO = {
        sql"""SELECT paymentId, amount, payerId, receivedAt FROM payment
             |WHERE payerId = ${payerId.id}
           """.stripMargin
          .query[Payment]
          .to[List]
          .transact(tx)
      }

      for {
        invoices <- invoicesIO
        payments <- paymentsIO
      } yield Balance(payerId.id, calculateBalance(date, invoices, payments))
    }

    /** The balance for a given [[Payer]] is the amount that the payer still has left to pay (if negative)
      * or has paid in advance (if positive) on a given date (at midnight).
      *
      * If the balance is zero, then all invoices sent before the given date have been paid in full,
      * and nothing has been paid in advance.
      *
      * @param date The date for which the balance should be calculated
      * @param allInvoices The invoices to take into account for the balance
      * @param allPayments The payments to take into account for the balance
      */
    private def calculateBalance(
                                  date: LocalDate,
                                  allInvoices: List[Invoice],
                                  allPayments: List[Payment]
                                ): Double = {
      val startOfDay = date.atStartOfDay(ZoneOffset.UTC).toLocalDateTime

      val filteredSortedInvoices = allInvoices
        .filter(_.sentAt.isBefore(startOfDay))
        .sortBy(_.sentAt)

      val filteredSortedPayments = allPayments
        .filter(_.receivedAt.isBefore(startOfDay))
        .sortBy(_.receivedAt)

      val invoiceTotal = filteredSortedInvoices.map(_.total).sum
      val paymentTotal = filteredSortedPayments.map(_.amount).sum

      invoiceTotal + paymentTotal
    }
  }

  def routes(payers: Payers[IO]): HttpRoutes[IO] = {
    val dsl = new Http4sDsl[IO] {}
    import dsl._

    HttpRoutes.of[IO] {
      case GET -> Root / "payer" / IntVar(payerId) =>
        payers.get(PayerId(payerId)).flatMap {
          case Some(payer) => Ok(payer)
          case None        => NotFound()
        }

      case req @ POST -> Root / "payer" =>
        req.decode[NewPayer] { input =>
          payers.create(input).flatMap(Ok(_))
        }

      case GET -> Root / "payer" / payerId / "balance" / date =>
        payers.balance(PayerId(payerId.toInt), LocalDate.parse(date)).flatMap(Ok(_))
    }
  }
}
