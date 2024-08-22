package challenge.model.payments

import cats.Applicative
import cats.effect.Sync
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}

object PaymentId {

  final case class PaymentId(id: Int)

  implicit val codec: Codec[PaymentId] = deriveCodec[PaymentId]
  implicit def entityDecoder[F[_]: Sync]: EntityDecoder[F, PaymentId] = jsonOf
  implicit def entityEncoder[F[_]: Applicative]: EntityEncoder[F, PaymentId] = jsonEncoderOf

}
