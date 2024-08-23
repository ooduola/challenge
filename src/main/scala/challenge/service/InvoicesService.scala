package challenge.service

import cats.effect.IO
import challenge.model.invoices.Invoice.Invoice
import challenge.model.invoices.InvoiceId.InvoiceId
import challenge.model.invoices.NewInvoice.NewInvoice
import challenge.repository.InvoiceRepository
import doobie.util.transactor.Transactor


trait InvoicesService[F[_]] {
  def get(id: InvoiceId): F[Option[Invoice]]
  def create(invoice: NewInvoice): F[InvoiceId]
}

object InvoicesService {

  def impl(repo: InvoiceRepository[IO])(implicit tx: Transactor[IO]): InvoicesService[IO] = new InvoicesService[IO] {
    override def get(invoiceId: InvoiceId): IO[Option[Invoice]] =
      repo.get(invoiceId)

    override def create(newInvoice: NewInvoice): IO[InvoiceId] =
      repo.create(newInvoice)

  }
}
