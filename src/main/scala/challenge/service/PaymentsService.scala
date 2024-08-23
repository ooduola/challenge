package challenge.service

import cats.effect.IO
import challenge.model.payments.NewPayment.NewPayment
import challenge.model.payments.Payment.Payment
import challenge.model.payments.PaymentId.PaymentId
import challenge.repository.PaymentRepository


trait PaymentsService[F[_]] {
  def get(id: PaymentId): F[Option[Payment]]
  def create(payment: NewPayment): F[PaymentId]
}

object PaymentsService {
  def impl(repository: PaymentRepository[IO]): PaymentsService[IO] = new PaymentsService[IO] {
    override def get(paymentId: PaymentId): IO[Option[Payment]] =
      repository.get(paymentId)

    override def create(newPayment: NewPayment): IO[PaymentId] =
      repository.create(newPayment)
  }
}
