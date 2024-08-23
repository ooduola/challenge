package challenge

import cats.effect.IO
import challenge.model.invoices.Invoice.Invoice
import challenge.model.invoices.InvoiceId.InvoiceId
import challenge.model.invoices.NewInvoice.NewInvoice
import challenge.repository.InvoiceRepository
import doobie.implicits._
import doobie.implicits.javatime._
import doobie.util.transactor.Transactor
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl


trait Invoices[F[_]] {
  def get(id: InvoiceId): F[Option[Invoice]]
  def create(invoice: NewInvoice): F[InvoiceId]
}

object Invoices {

  def impl(repo: InvoiceRepository[IO])(implicit tx: Transactor[IO]): Invoices[IO] = new Invoices[IO] {
    override def get(invoiceId: InvoiceId): IO[Option[Invoice]] =
      repo.get(invoiceId)

    override def create(newInvoice: NewInvoice): IO[InvoiceId] =
      repo.create(newInvoice)

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
