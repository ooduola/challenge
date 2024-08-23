//package challenge.service
//
//import cats.effect.IO
//import challenge.Payments._
//import challenge.Invoices._
//import challenge.repository.{InvoiceRepository, PaymentRepository}
//
//class PaymentService(paymentRepo: PaymentRepository, invoiceRepo: InvoiceRepository) {
//
//  def allocatePayment(payment: Payment): IO[Unit] = {
//    for {
//      unpaidInvoices <- invoiceRepo.getUnpaidInvoices(payment.payerId)
//      _ <- allocateToInvoices(payment, unpaidInvoices)
//    } yield ()
//  }
//
//  private def allocateToInvoices(payment: Payment, unpaidInvoices: List[Invoice]): IO[Unit] = {
//    unpaidInvoices.foldLeft(IO.pure(payment.amount)) { (remainingAmountIO, invoice) =>
//      remainingAmountIO.flatMap { remainingAmount =>
//        val invoiceRemaining = invoice.total - invoice.paid
//
//        if (remainingAmount > 0 && invoiceRemaining > 0) {
//          val amountToAllocate = Math.min(remainingAmount, invoiceRemaining)
//          for {
//            _ <- paymentRepo.allocatePaymentToInvoice(payment.paymentId, invoice.invoiceId, amountToAllocate)
//            _ <- invoiceRepo.updateInvoicePayment(invoice.invoiceId, amountToAllocate)
//          } yield remainingAmount - amountToAllocate
//        } else {
//          IO.pure(remainingAmount)
//        }
//      }
//    }.void
//  }
//}
