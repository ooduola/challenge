package challenge.repository

import cats.effect.IO
import doobie.ConnectionIO
import doobie.implicits._
import doobie.util.transactor.Transactor

trait PaymentInvoiceRepository[F[_]] {
  def linkPaymentToInvoice(paymentId: Int, invoiceId: Int, amountPaid: Double): F[Unit]
}

class PaymentInvoiceRepositoryImpl(implicit tx: Transactor[IO]) extends PaymentInvoiceRepository[IO] {

  override def linkPaymentToInvoice(paymentId: Int, invoiceId: Int, amountPaid: Double): IO[Unit] = {
    val query =
      sql"""
        INSERT INTO payment_invoice (paymentId, invoiceId, amountPaid)
        VALUES ($paymentId, $invoiceId, $amountPaid)
        ON DUPLICATE KEY UPDATE amountPaid = VALUES(amountPaid)
      """.update.run

    query.transact(tx).map(_ => ())
  }
}