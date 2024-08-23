package challenge.model.invoices

import cats.Applicative
import cats.effect.Sync
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}

import java.time.LocalDateTime

object NewInvoice {

  final case class NewInvoice(total: Double, payerId: Int, sentAt: Option[LocalDateTime])

  implicit val codec: Codec[NewInvoice] = deriveCodec[NewInvoice]
  implicit def entityDecoder[F[_]: Sync]: EntityDecoder[F, NewInvoice] = jsonOf
  implicit def entityEncoder[F[_]: Applicative]: EntityEncoder[F, NewInvoice] = jsonEncoderOf

}
