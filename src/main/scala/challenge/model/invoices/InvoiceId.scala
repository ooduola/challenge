package challenge.model.invoices

import cats.Applicative
import cats.effect.Sync
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}

object InvoiceId {


  final case class InvoiceId(id: Int)

  implicit val codec: Codec[InvoiceId] = deriveCodec[InvoiceId]
  implicit def entityDecoder[F[_]: Sync]: EntityDecoder[F, InvoiceId] = jsonOf
  implicit def entityEncoder[F[_]: Applicative]: EntityEncoder[F, InvoiceId] = jsonEncoderOf

}
