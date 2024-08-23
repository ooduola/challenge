package challenge.model.payers

import cats.Applicative
import cats.effect.Sync
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}

object Payers {

  /** A [[Payer]] is a person or entity to which invoices can be sent
   * and from which payments can be received.
   */
  final case class Payer(payerId: Int, name: String)

  // JSON codec for marshalling to-and-from JSON
  implicit val codec: Codec[Payer] = deriveCodec[Payer]

  // Codecs for reading/writing HTTP entities (uses the above JSON codec)
  implicit def entityDecoder[F[_]: Sync]: EntityDecoder[F, Payer] = jsonOf
  implicit def entityEncoder[F[_]: Applicative]: EntityEncoder[F, Payer] = jsonEncoderOf

}
