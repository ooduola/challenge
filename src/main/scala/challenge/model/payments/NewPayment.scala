package challenge.model.payments

import cats.Applicative
import cats.effect.Sync
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}

import java.time.LocalDateTime

object NewPayment {

  final case class NewPayment(amount: Double, payerId: Int, receivedAt: Option[LocalDateTime])

  implicit val codec: Codec[NewPayment] = deriveCodec[NewPayment]
  implicit def entityDecoder[F[_]: Sync]: EntityDecoder[F, NewPayment] = jsonOf
  implicit def entityEncoder[F[_]: Applicative]: EntityEncoder[F, NewPayment] = jsonEncoderOf

}
