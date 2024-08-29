package challenge

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext.global
import cats.effect.Blocker
import cats.effect.IO
import challenge.http._
import challenge.model.invoices.Invoice.Invoice
import challenge.model.invoices.InvoiceId.InvoiceId
import challenge.model.invoices.NewInvoice.NewInvoice
import challenge.model.payers.Balance.Balance
import challenge.model.payers.NewPayer.NewPayer
import challenge.model.payers.PayerId.PayerId
import challenge.model.payers.Payers.Payer
import challenge.model.payments.NewPayment.NewPayment
import challenge.model.payments.Payment.Payment
import challenge.model.payments.PaymentId.PaymentId
import challenge.repository._
import challenge.service.{InvoicesService, PayersService, PaymentsService}
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import org.http4s.{HttpRoutes, Uri}
import org.http4s.client.dsl.io._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.mockito.MockitoSugar
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers


class ChallengeTest extends AnyFreeSpec with Matchers with MockitoSugar {
  val blockingPool = Executors.newFixedThreadPool(5)
  val blocker = Blocker.liftExecutorService(blockingPool)
  implicit val cs = IO.contextShift(ExecutionContexts.synchronous)

  val config = new HikariConfig()
  config.setDriverClassName("com.mysql.cj.jdbc.Driver")
  config.setJdbcUrl("jdbc:mysql://127.0.0.1:3306/challenge")
  config.setUsername("root")
  config.setPassword("root")

  // Use this Transactor with the HikariConfig above for MySQL
  implicit val tx: HikariTransactor[IO] = HikariTransactor(new HikariDataSource(config), global, blocker)

  val payerRepo = new PayerRepositoryImpl
  val paymentRepo = new PaymentRepositoryImpl
  val invoiceRepo = new InvoiceRepositoryImpl
  val paymentInvoiceRepo = new PaymentInvoiceRepositoryImpl

  val payersService: PayersService[IO] = PayersService.impl(payerRepo)
  val invoicesService: InvoicesService[IO] = InvoicesService.impl(invoiceRepo)
  val paymentsService: PaymentsService[IO] = PaymentsService.impl(paymentRepo, invoiceRepo, paymentInvoiceRepo)
  
  val invoiceRoutes: HttpRoutes[IO] = new InvoiceRoutes(invoicesService).routes
  val paymentRoutes: HttpRoutes[IO] = new PaymentRoutes(paymentsService).routes
  val payersRoutes: HttpRoutes[IO] = new PayersRoutes(payersService).routes
  

  "PayersService" - {
    "should be creatable" in {
      val io = for {
        createReq <- POST(NewPayer("Mr Jameson"), uri"""http://0.0.0.0:8080/payer""")
        payerId <- payersRoutes.orNotFound.run(createReq).flatMap(_.as[PayerId])

        fetchReq <- GET(uri"""http://0.0.0.0:8080/payer/""".addSegment(payerId.id.toString))
        payer <- payersRoutes.orNotFound.run(fetchReq).flatMap(_.as[Payer])
      } yield payer should matchPattern { case Payer(id, _) if id == payerId.id => }

      io.unsafeRunSync()
    }

    "should have the right balance after adding invoices and payments" in {
      val io = for {
        payerReq <- POST(NewPayer("Mrs Brodie"), uri"""http://0.0.0.0:8080/payer""")
        payerId <- payersRoutes.orNotFound.run(payerReq).flatMap(_.as[PayerId])

        // Add invoice of -100
        invReq1 <- POST(
          NewInvoice(-100, payerId.id, Some(LocalDateTime.of(2020, 10, 10, 14, 30))),
          uri"""http://0.0.0.0:8080/invoice"""
        )
        _ <- invoiceRoutes.orNotFound.run(invReq1).flatMap(_.as[Unit])

        // Add payment of 50
        paymentReq <- POST(
          NewPayment(50, payerId.id, Some(LocalDateTime.of(2020, 10, 10, 14, 45))),
          uri"""http://0.0.0.0:8080/payment"""
        )
        _ <- paymentRoutes.orNotFound.run(paymentReq).flatMap(_.as[Unit])

        // Add invoice of -100
        invReq2 <- POST(
          NewInvoice(-100, payerId.id, Some(LocalDateTime.of(2020, 10, 10, 17, 30))),
          uri"""http://0.0.0.0:8080/invoice"""
        )
        _ <- invoiceRoutes.orNotFound.run(invReq2).flatMap(_.as[Unit])

        // Add invoice of -250
        invReq3 <- POST(
          NewInvoice(-250, payerId.id, Some(LocalDateTime.of(2020, 10, 11, 11, 30))),
          uri"""http://0.0.0.0:8080/invoice"""
        )
        _ <- invoiceRoutes.orNotFound.run(invReq3).flatMap(_.as[Unit])

        // Add payment of 100
        paymentReq <- POST(
          NewPayment(100, payerId.id, Some(LocalDateTime.of(2020, 10, 12, 11, 27))),
          uri"""http://0.0.0.0:8080/payment"""
        )
        _ <- paymentRoutes.orNotFound.run(paymentReq).flatMap(_.as[Unit])

        // Get balance on 2020-10-11
        targetDate = LocalDate.of(2020, 10, 11).format(DateTimeFormatter.ISO_DATE)
        balancePath <- IO.fromEither(Uri.fromString(s"http://0.0.0.0:8080/payer/${payerId.id}/balance/$targetDate"))
        balanceReq <- GET(balancePath)
        balance <- payersRoutes.orNotFound.run(balanceReq).flatMap(_.as[Balance])
      } yield assertResult(-150)(balance.balance)

      io.unsafeRunSync()
    }

    "should fallback to calculate balance when DB balance is not available" in {
      val mockPayerRepo = mock[PayerRepository[IO]]
      val payersService = PayersService.impl(mockPayerRepo)

      val payerId = PayerId(1)
      val testDate = LocalDate.of(2020, 10, 11)
      val testStartOfDay = testDate.atStartOfDay()

      // Mock repository to return None for balance
      when(mockPayerRepo.getBalance(payerId, testDate)).thenReturn(IO.pure(None))

      // Mock repository to return test invoices and payments
      val invoices = List(
        Invoice(1, -100, payerId.id, testStartOfDay.minusHours(2)),
        Invoice(2, -50, payerId.id, testStartOfDay.minusHours(1))
      )

      val payments = List(
        Payment(1, 100, payerId.id, testStartOfDay.minusHours(1)),
        Payment(2, 50, payerId.id, testStartOfDay.plusHours(4))
      )

      when(mockPayerRepo.getInvoices(payerId, testStartOfDay)).thenReturn(IO.pure(invoices))
      when(mockPayerRepo.getPayments(payerId, testStartOfDay)).thenReturn(IO.pure(payments))

      val balanceResult = payersService.balance(payerId, testDate).unsafeRunSync()

      balanceResult shouldEqual Balance(payerId.id, -50.0)
    }

  }

  "InvoicesService" - {
    "should be creatable" in {
      val io = for {
        payerReq <- POST(NewPayer("Ms Ferrara"), uri"""http://0.0.0.0:8080/payer""")
        payerId <- payersRoutes.orNotFound.run(payerReq).flatMap(_.as[PayerId])

        invCreateReq <- POST(NewInvoice(100, payerId.id, None), uri"""http://0.0.0.0:8080/invoice""")
        invoiceId <- invoiceRoutes.orNotFound.run(invCreateReq).flatMap(_.as[InvoiceId])

        invFetchReq <- GET(uri"""http://0.0.0.0:8080/invoice""".addSegment(invoiceId.id.toString))
        invoice <- invoiceRoutes.orNotFound.run(invFetchReq).flatMap(_.as[Invoice])
      } yield invoice should matchPattern { case Invoice(id, _, _, _) if id == invoiceId.id => }

      io.unsafeRunSync()
    }
  }

  "PaymentsService" - {
    "should be creatable" in {
      val io = for {
        payerReq <- POST(NewPayer("Dr Theodore"), uri"""http://0.0.0.0:8080/payer""")
        payerId <- payersRoutes.orNotFound.run(payerReq).flatMap(_.as[PayerId])

        paymentCreateReq <- POST(NewPayment(75, payerId.id, None), uri"""http://0.0.0.0:8080/payment""")
        paymentId <- paymentRoutes.orNotFound.run(paymentCreateReq).flatMap(_.as[PaymentId])

        paymentFetchReq <- GET(uri"""http://0.0.0.0:8080/payment""".addSegment(paymentId.id.toString))
        payment <- paymentRoutes.orNotFound.run(paymentFetchReq).flatMap(_.as[Payment])
      } yield payment should matchPattern { case Payment(id, _, _, _) if id == paymentId.id => }

      io.unsafeRunSync()
    }
  }
}
