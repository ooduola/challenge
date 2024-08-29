package challenge.service

import cats.effect.IO
import challenge.model.invoices.Invoice.Invoice
import challenge.model.payments.NewPayment.NewPayment
import challenge.model.payments.Payment.Payment
import challenge.model.payments.PaymentId.PaymentId
import challenge.repository.{InvoiceRepository, PaymentInvoiceRepository, PaymentRepository}

trait PaymentsService[F[_]] {
  def get(id: PaymentId): F[Option[Payment]]
  def create(payment: NewPayment): F[PaymentId]
  def getInvoicesByPayment(paymentId: PaymentId): F[List[Invoice]]
}

object PaymentsService {
  def impl(
            paymentRepository: PaymentRepository[IO],
            invoiceRepository: InvoiceRepository[IO],
            paymentInvoiceRepository: PaymentInvoiceRepository[IO]
          ): PaymentsService[IO] = new PaymentsService[IO] {

    override def get(paymentId: PaymentId): IO[Option[Payment]] =
      paymentRepository.get(paymentId)

    override def create(newPayment: NewPayment): IO[PaymentId] =
      for {
        paymentId <- paymentRepository.create(newPayment)
        _         <- allocatePaymentToInvoices(paymentId.id, newPayment.payerId, newPayment.amount)
      } yield paymentId

    override def getInvoicesByPayment(paymentId: PaymentId): IO[List[Invoice]]  =
      invoiceRepository.getInvoicesByPayment(paymentId.id)

    private def allocatePaymentToInvoices(paymentId: Int, payerId: Int, amount: Double): IO[Unit] = {
      val unpaidInvoices = invoiceRepository.getUnpaidInvoicesByPayer(payerId)

      unpaidInvoices.map { invoices =>
        invoices.foldLeft(amount) { (remainingAmount, invoice) =>
          val amountToPay = math.min(remainingAmount, invoice.total)
          if (amountToPay > 0) {
            paymentInvoiceRepository.linkPaymentToInvoice(paymentId, invoice.invoiceId, amountToPay)
          }
          remainingAmount - amountToPay
        }
      }
    }

  }
}
