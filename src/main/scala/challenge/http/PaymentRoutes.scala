package challenge.http

import cats.effect.IO
import challenge.model.payments.NewPayment.NewPayment
import challenge.model.payments.PaymentId.PaymentId
import challenge.service.PaymentsService
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

class PaymentRoutes(paymentService: PaymentsService[IO]) extends Http4sDsl[IO] {

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {

    case GET -> Root / "payment" / IntVar(paymentId) =>
      paymentService.get(PaymentId(paymentId)).flatMap {
        case Some(payment) => Ok(payment)
        case None          => NotFound()
      }

    case req @ POST -> Root / "payment" =>
      req.decode[NewPayment] { input =>
        paymentService.create(input).flatMap(Ok(_))
      }
  }
}
