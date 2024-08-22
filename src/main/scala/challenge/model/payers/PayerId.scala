package challenge.model.payers

import cats.Applicative
import cats.effect.Sync
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}

object PayerId {

  final case class PayerId(id: Int)

  implicit val codec: Codec[PayerId] = deriveCodec[PayerId]
  implicit def entityDecoder[F[_] : Sync]: EntityDecoder[F, PayerId] = jsonOf
  implicit def entityEncoder[F[_] : Applicative]: EntityEncoder[F, PayerId] = jsonEncoderOf

}
