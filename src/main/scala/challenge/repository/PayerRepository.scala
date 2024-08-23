package challenge.repository


import cats.effect.IO
import challenge.model.invoices.Invoice.Invoice
import challenge.model.payers.NewPayer.NewPayer
import challenge.model.payers.PayerId.PayerId
import challenge.model.payers.Payers.Payer
import challenge.model.payments.Payment.Payment
import doobie.implicits._
import doobie.implicits.javatime._
import doobie.util.transactor.Transactor

import java.time.{LocalDate, LocalDateTime, ZoneOffset}

trait PayerRepository[F[_]] {
  def get(id: PayerId): F[Option[Payer]]
  def create(payer: NewPayer): F[PayerId]
  def getBalance(payerId: PayerId, date: LocalDate): F[Option[Double]]
  def getInvoices(payerId: PayerId, date: LocalDateTime): F[List[Invoice]]
  def getPayments(payerId: PayerId, date: LocalDateTime): F[List[Payment]]
}

class PayerRepositoryImpl(implicit tx: Transactor[IO])extends PayerRepository[IO] {

  override def get(id: PayerId): IO[Option[Payer]] = {
    sql"""SELECT payerId, name FROM payer WHERE payerId = ${id.id}"""
      .query[Payer]
      .to[List]
      .transact(tx)
      .map(_.headOption)
  }

  override def create(payer: NewPayer): IO[PayerId] = {
    val query = sql"""INSERT INTO payer (name) VALUES (${payer.name})"""
      .update
      .withUniqueGeneratedKeys[Int]("payerId")

    query.transact(tx).map(PayerId)
  }

  override def getBalance(payerId: PayerId, date: LocalDate): IO[Option[Double]] = {
    val startOfDay = date.atStartOfDay(ZoneOffset.UTC).toLocalDateTime
    sql"""
         |SELECT balance
         |FROM balance
         |WHERE payerId = ${payerId.id} AND balanceDate <= $startOfDay
         |ORDER BY balanceDate DESC
         |LIMIT 1
    """.stripMargin
      .query[Double]
      .option
      .transact(tx)
  }

  override def getInvoices(payerId: PayerId, date: LocalDateTime): IO[List[Invoice]] = {
    sql"""SELECT invoiceId, total, payerId, sentAt
         |FROM invoice
         |WHERE payerId = ${payerId.id}
         |AND sentAt <= $date
       """.stripMargin
      .query[Invoice]
      .to[List]
      .transact(tx)
  }

  override def getPayments(payerId: PayerId, date: LocalDateTime): IO[List[Payment]] = {
    sql"""
         |SELECT paymentId, amount, payerId, receivedAt
         |FROM payment
         |WHERE payerId = ${payerId.id}
         |AND receivedAt <= $date
       """.stripMargin
      .query[Payment]
      .to[List]
      .transact(tx)
  }
}

