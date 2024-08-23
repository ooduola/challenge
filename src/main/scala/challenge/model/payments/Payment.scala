package challenge.model.payments

import cats.Applicative
import cats.effect.Sync
import challenge.service.{InvoicesService, PayersService}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}

import java.time.LocalDateTime

object Payment {

  /** A [[Payment]] represents an amount of money paid against an [[InvoicesService.Invoice]] issued to the given [[PayersService.Payer]].
   *
   * Payments can be made in advance, so it is fine for the amount on a payment to be greater than the total amount owed.
   *
   * @param amount     The amount paid by the [[PayersService.Payer]]. Usually positive.
   * @param payerId    The ID of the [[PayersService.Payer]] paying the given amount.
   * @param receivedAt The time at which the payment was made
   */
  final case class Payment(paymentId: Int, amount: Double, payerId: Int, receivedAt: LocalDateTime)

  // JSON codec for marshalling to-and-from JSON
  implicit val encoder: Codec[Payment] = deriveCodec[Payment]

  // Codecs for reading/writing HTTP entities (uses the above JSON codec)
  implicit def entityDecoder[F[_]: Sync]: EntityDecoder[F, Payment] = jsonOf
  implicit def entityEncoder[F[_]: Applicative]: EntityEncoder[F, Payment] = jsonEncoderOf


}
