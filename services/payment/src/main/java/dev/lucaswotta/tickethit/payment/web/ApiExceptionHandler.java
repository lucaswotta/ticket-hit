package dev.lucaswotta.tickethit.payment.web;

import dev.lucaswotta.tickethit.payment.charge.ChargeNotFoundException;
import dev.lucaswotta.tickethit.payment.charge.IdempotencyKeyReusedException;
import dev.lucaswotta.tickethit.payment.charge.PaymentUnavailableException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Erros em Problem Details (RFC 9457), com o campo {@code code} estável. */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ChargeNotFoundException.class)
    ProblemDetail notFound(ChargeNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", exception);
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ProblemDetail keyReused(IdempotencyKeyReusedException exception) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "IDEMPOTENCY_KEY_REUSED", exception);
    }

    @ExceptionHandler(PaymentUnavailableException.class)
    ResponseEntity<ProblemDetail> unavailable(PaymentUnavailableException exception) {
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
