package challenge.http

import cats.effect.IO
import challenge.model.invoices.InvoiceId.InvoiceId
import challenge.model.invoices.NewInvoice._
import challenge.service.InvoicesService
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

class InvoiceRoutes(invoiceService: InvoicesService[IO]) extends Http4sDsl[IO] {

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {

    case GET -> Root / "invoice" / IntVar(invoiceId) =>
      invoiceService.get(InvoiceId(invoiceId)).flatMap {
        case Some(invoice) => Ok(invoice)
        case None          => NotFound()
      }

    case req @ POST -> Root / "invoice" =>
      req.decode[NewInvoice] { input =>
        invoiceService.create(input).flatMap { invoiceId =>
          Created(invoiceId)
        }
      }
  }
}
