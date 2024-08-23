package challenge.http

import cats.effect.IO
import challenge.model.payers.NewPayer.NewPayer
import challenge.model.payers.PayerId.PayerId
import challenge.service.PayersService
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

import java.time.LocalDate

class PayersRoutes(payersService: PayersService[IO]) extends Http4sDsl[IO] {

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {

      case GET -> Root / "payer" / IntVar(payerId) =>
        payersService.get(PayerId(payerId)).flatMap {
          case Some(payer) => Ok(payer)
          case None        => NotFound()
        }

      case req @ POST -> Root / "payer" =>
        req.decode[NewPayer] { input =>
          payersService.create(input).flatMap(Ok(_))
        }

      case GET -> Root / "payer" / payerId / "balance" / date =>
        payersService.balance(PayerId(payerId.toInt), LocalDate.parse(date)).flatMap(Ok(_))
    }

  }
