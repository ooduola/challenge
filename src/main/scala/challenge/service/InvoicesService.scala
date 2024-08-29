package challenge.service

import cats.effect.IO
import challenge.model.invoices.Invoice.Invoice
import challenge.model.invoices.InvoiceId.InvoiceId
import challenge.model.invoices.NewInvoice.NewInvoice
import challenge.model.payments.Payment.Payment
import challenge.repository.InvoiceRepository
import doobie.util.transactor.Transactor


trait InvoicesService[F[_]] {
  def get(id: InvoiceId): F[Option[Invoice]]
  def create(invoice: NewInvoice): F[InvoiceId]
  def getPaymentsByInvoice(invoiceId: InvoiceId): F[List[Payment]]
}

object InvoicesService {

  def impl(invoiceRepo: InvoiceRepository[IO])(implicit tx: Transactor[IO]): InvoicesService[IO] = new InvoicesService[IO] {
    override def get(invoiceId: InvoiceId): IO[Option[Invoice]] =
      invoiceRepo.get(invoiceId)

    override def create(newInvoice: NewInvoice): IO[InvoiceId] =
      invoiceRepo.create(newInvoice)

    override def getPaymentsByInvoice(invoiceId: InvoiceId): IO[List[Payment]] =
      invoiceRepo.getPaymentsByInvoice(invoiceId.id)
  }
}
