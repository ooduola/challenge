package challenge.model.payers

import cats.Applicative
import cats.effect.Sync
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}

object Balance {

  final case class Balance(payerId: Int, balance: Double)

  implicit val codec: Codec[Balance] = deriveCodec[Balance]
  implicit def entityDecoder[F[_]: Sync]: EntityDecoder[F, Balance] = jsonOf
  implicit def entityEncoder[F[_]: Applicative]: EntityEncoder[F, Balance] = jsonEncoderOf
}
