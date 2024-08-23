package challenge.model.invoices

import cats.Applicative
import cats.effect.Sync
import challenge.service.PayersService
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}

import java.time.LocalDateTime

object Invoice {

  /** An [[Invoice]] is a document issued to a given [[PayersService.Payer]],
   * detailing an amount owed by that payer for various goods and services provided.
   *
   * Here we are omitting the details, and only care about the amount owed and
   * who needs to pay said amount.
   *
   * To make things easy, an invoice is payable immediately after being sent,
   * so the amount on the invoice is owed immediately after the invoice has been sent.
   *
   * @param total The total payable amount on the invoice. Usually negative.
   * @param payerId The ID of the payer who receives the invoice
   * @param sentAt The time at which the invoice becomes payable
   */
  final case class Invoice(invoiceId: Int, total: Double, payerId: Int, sentAt: LocalDateTime)

  // JSON codec for marshalling to-and-from JSON
  implicit val codec: Codec[Invoice] = deriveCodec[Invoice]

  // Codecs for reading/writing HTTP entities (uses the above JSON codec)
  implicit def entityDecoder[F[_]: Sync]: EntityDecoder[F, Invoice] = jsonOf
  implicit def entityEncoder[F[_]: Applicative]: EntityEncoder[F, Invoice] = jsonEncoderOf


}
