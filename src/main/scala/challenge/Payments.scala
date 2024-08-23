package challenge

import cats.effect.IO
import challenge.model.payments.NewPayment.NewPayment
import challenge.model.payments.Payment.Payment
import challenge.model.payments.PaymentId.PaymentId
import challenge.repository.PaymentRepository
import doobie.implicits.javatime._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl


trait Payments[F[_]] {
  def get(id: PaymentId): F[Option[Payment]]
  def create(payment: NewPayment): F[PaymentId]
}

object Payments {
  def impl(repository: PaymentRepository[IO]): Payments[IO] = new Payments[IO] {
    override def get(paymentId: PaymentId): IO[Option[Payment]] =
      repository.get(paymentId)

    override def create(newPayment: NewPayment): IO[PaymentId] =
      repository.create(newPayment)
  }

  def routes(payments: Payments[IO]): HttpRoutes[IO] = {
    val dsl = new Http4sDsl[IO] {}
    import dsl._

    HttpRoutes.of[IO] {
      case GET -> Root / "payment" / IntVar(paymentId) =>
        payments.get(PaymentId(paymentId)).flatMap {
          case Some(payment) => Ok(payment)
          case None          => NotFound()
        }

      case req @ POST -> Root / "payment" =>
        req.decode[NewPayment] { input =>
          payments.create(input).flatMap(Ok(_))
        }
    }
  }
}
