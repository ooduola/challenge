package challenge

import cats.effect.IO
import challenge.model.invoices.Invoice.Invoice
import challenge.model.payers.Balance.Balance
import challenge.model.payers.NewPayer.NewPayer
import challenge.model.payers.PayerId.PayerId
import challenge.model.payers.Payers.Payer
import challenge.model.payments.Payment.Payment
import challenge.repository.PayerRepository
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

import java.time.{LocalDate, LocalDateTime, ZoneOffset}

trait Payers[F[_]] {
  def get(id: PayerId): F[Option[Payer]]
  def create(payer: NewPayer): F[PayerId]
  def balance(payerId: PayerId, date: LocalDate): F[Balance]
}

object Payers {

  def impl(repository: PayerRepository[IO])(implicit tx: Transactor[IO]): Payers[IO] = new Payers[IO] {
    override def get(payerId: PayerId): IO[Option[Payer]] = {
      repository.get(payerId)
    }

    override def create(newPayer: NewPayer): IO[PayerId] = {
      repository.create(newPayer)
    }

    override def balance(payerId: PayerId, date: LocalDate): IO[Balance] = {
      repository.getBalance(payerId, date).flatMap {
        case Some(balance) =>
          IO.pure(Balance(payerId.id, balance))

        case _ =>
          calculateFallbackBalance(payerId, date)
      }
    }

    private def calculateFallbackBalance(payerId: PayerId, date: LocalDate): IO[Balance] = {
      val startOfDay = date.atStartOfDay(ZoneOffset.UTC).toLocalDateTime

      for {
        invoices <- repository.getInvoices(payerId, startOfDay)
        payments <- repository.getPayments(payerId, startOfDay)
      } yield {
        val balance = calculateBalance(startOfDay, invoices, payments)
        Balance(payerId.id, balance)
      }
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
                                  date: LocalDateTime,
                                  allInvoices: List[Invoice],
                                  allPayments: List[Payment]
                                ): Double = {

      val filteredSortedInvoices = allInvoices
        .filter(_.sentAt.isBefore(date))
        .sortBy(_.sentAt)

      val filteredSortedPayments = allPayments
        .filter(_.receivedAt.isBefore(date))
        .sortBy(_.receivedAt)

      val invoiceTotal = filteredSortedInvoices.map(_.total).sum
      val paymentTotal = filteredSortedPayments.map(_.amount).sum

      invoiceTotal - paymentTotal
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
