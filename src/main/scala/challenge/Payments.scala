package challenge

import cats.effect.IO
import challenge.model.payments.NewPayment.NewPayment
import challenge.model.payments.Payment.Payment
import challenge.model.payments.PaymentId.PaymentId
import challenge.utils.DateTimeUtils._
import doobie.ConnectionIO
import doobie.implicits._
import doobie.implicits.javatime._
import doobie.util.transactor.Transactor
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

import java.time.LocalDateTime


trait Payments[F[_]] {
  def get(id: PaymentId): F[Option[Payment]]
  def create(payment: NewPayment): F[PaymentId]
}

object Payments {

  def impl(tx: Transactor[IO]): Payments[IO] = new Payments[IO] {
    override def get(paymentId: PaymentId): IO[Option[Payment]] = {
      sql"""SELECT paymentId, amount, payerId, receivedAt FROM payment WHERE paymentId = ${paymentId.id}"""
        .query[Payment]
        .to[List]
        .transact(tx)
        .map(_.headOption)
    }

    override def create(newPayment: NewPayment): IO[PaymentId] = {
      val receivedAt = newPayment.receivedAt
        .map(toUtc)
        .getOrElse(toUtc(LocalDateTime.now()))

      val createPaymentQuery = for {
        paymentId <- insertPayment(newPayment, receivedAt)
        previousBalance <- getPreviousBalance(newPayment.payerId, receivedAt)
        newBalance = previousBalance.getOrElse(0.0) + newPayment.amount
        _ <- updateBalance(newPayment.payerId, receivedAt, newBalance)
      } yield PaymentId(paymentId)

      createPaymentQuery.transact(tx)
    }

    private def insertPayment(newPayment: NewPayment, receivedAt: LocalDateTime): ConnectionIO[Int] =
      sql"""
            |INSERT INTO payment (amount, payerId, receivedAt)
            |VALUES (${newPayment.amount}, ${newPayment.payerId}, $receivedAt)
    """.stripMargin.update.withUniqueGeneratedKeys[Int]("paymentId")

    private def getPreviousBalance(payerId: Int, receivedAt: LocalDateTime): ConnectionIO[Option[Double]] =
      sql"""
            |SELECT balance
            |FROM balance
            |WHERE payerId = $payerId AND balanceDate <= $receivedAt
            |ORDER BY balanceDate DESC
            |LIMIT 1
    """.stripMargin.query[Double].option

    private def updateBalance(payerId: Int, balanceDate: LocalDateTime, balance: Double): ConnectionIO[Int] =
      sql"""
            |INSERT INTO balance (payerId, balanceDate, balance)
            |VALUES ($payerId, $balanceDate, $balance)
            |ON DUPLICATE KEY UPDATE balance = VALUES(balance)
    """.stripMargin.update.run
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
