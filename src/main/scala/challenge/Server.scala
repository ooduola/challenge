package challenge

import cats.effect.{ConcurrentEffect, IO, Timer}
import cats.implicits._
import challenge.http._
import challenge.repository.{InvoiceRepositoryImpl, PayerRepositoryImpl, PaymentInvoiceRepositoryImpl, PaymentRepositoryImpl}
import challenge.service.{InvoicesService, PayersService, PaymentsService}
import doobie.util.transactor.Transactor
import fs2._
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger

import scala.concurrent.ExecutionContext.global

object Server {
  val resources = Resources.MySQL("root", "root")

  def stream(tx: Transactor[IO])(implicit F: ConcurrentEffect[IO], T: Timer[IO]): Stream[IO, Nothing] = {

    implicit val transactor: Transactor[IO] = tx

    val payerRepo = new PayerRepositoryImpl
    val paymentRepo = new PaymentRepositoryImpl
    val invoiceRepo = new InvoiceRepositoryImpl
    val paymentInvoiceRepo = new PaymentInvoiceRepositoryImpl

    val invoicesImpl = InvoicesService.impl(invoiceRepo)
    val paymentsImpl = PaymentsService.impl(paymentRepo, invoiceRepo, paymentInvoiceRepo)
    val payersImpl = PayersService.impl(payerRepo)


    val invoiceRoutes = new InvoiceRoutes(invoicesImpl).routes
    val paymentRoutes = new PaymentRoutes(paymentsImpl).routes
    val payersRoutes = new PayersRoutes(payersImpl).routes

    // Compose our various routes. They are tried in turn until one matches.
    // If none match, we return a 404 response
    val httpApp = (invoiceRoutes <+> paymentRoutes <+> payersRoutes).orNotFound

    // With Middlewares in place
    val finalHttpApp = Logger.httpApp(logHeaders = true, logBody = true)(httpApp)

    BlazeServerBuilder[IO](global)
      .bindHttp(8080, "0.0.0.0")
      .withHttpApp(finalHttpApp)
      .serve
  }.drain
}
