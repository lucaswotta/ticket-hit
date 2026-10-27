package dev.lucaswotta.tickethit.ticketing.web;

import dev.lucaswotta.tickethit.ticketing.hold.HoldNotFoundException;
import dev.lucaswotta.tickethit.ticketing.hold.HoldNotPayableException;
import dev.lucaswotta.tickethit.ticketing.hold.InvalidQuantityException;
import dev.lucaswotta.tickethit.ticketing.hold.SoldOutException;
import dev.lucaswotta.tickethit.ticketing.payment.PaymentUnavailableException;
import dev.lucaswotta.tickethit.ticketing.sale.SaleNotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Erros no formato Problem Details (RFC 9457). O campo {@code code} é estável
 * para o front e para os bots decidirem o que fazer sem ler a mensagem.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler({SaleNotFoundException.class, HoldNotFoundException.class})
    ProblemDetail notFound(RuntimeException exception) {
        return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", exception);
    }

    @ExceptionHandler(SoldOutException.class)
    ProblemDetail soldOut(SoldOutException exception) {
        return problem(HttpStatus.CONFLICT, "SOLD_OUT", exception);
    }

    @ExceptionHandler(InvalidQuantityException.class)
    ProblemDetail invalidQuantity(InvalidQuantityException exception) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_QUANTITY", exception);
    }

    @ExceptionHandler(HoldNotPayableException.class)
    ProblemDetail notPayable(HoldNotPayableException exception) {
        return problem(HttpStatus.CONFLICT, "HOLD_NOT_PAYABLE", exception);
    }

    @ExceptionHandler(PaymentUnavailableException.class)
    ResponseEntity<ProblemDetail> paymentUnavailable(PaymentUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(problem(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_UNAVAILABLE", exception));
    }

    private static ProblemDetail problem(HttpStatus status, String code, RuntimeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setProperty("code", code);
        return problem;
    }
}
