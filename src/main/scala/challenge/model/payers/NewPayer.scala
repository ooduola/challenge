package challenge.model.payers

import cats.Applicative
import cats.effect.Sync
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}

object NewPayer {

  final case class NewPayer(name: String)

  implicit val codec: Codec[NewPayer] = deriveCodec[NewPayer]
  implicit def entityDecoder[F[_]: Sync]: EntityDecoder[F, NewPayer] = jsonOf
  implicit def entityEncoder[F[_]: Applicative]: EntityEncoder[F, NewPayer] = jsonEncoderOf

}
