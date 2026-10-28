package dev.lucaswotta.tickethit.simulator.web;

import dev.lucaswotta.tickethit.simulator.room.RoomNotFoundException;
import dev.lucaswotta.tickethit.simulator.room.RoomsFullException;
import dev.lucaswotta.tickethit.simulator.room.TooManyBotsException;
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

    @ExceptionHandler(RoomNotFoundException.class)
    ProblemDetail notFound(RoomNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", exception);
    }

    @ExceptionHandler(TooManyBotsException.class)
    ProblemDetail tooManyBots(TooManyBotsException exception) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "TOO_MANY_BOTS", exception);
    }

    // Retry-After de 30 s: uma sala dura em torno de um minuto depois da abertura.
    @ExceptionHandler(RoomsFullException.class)
    ResponseEntity<ProblemDetail> full(RoomsFullException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "30")
                .body(problem(HttpStatus.SERVICE_UNAVAILABLE, "ROOMS_FULL", exception));
    }

    private static ProblemDetail problem(HttpStatus status, String code, RuntimeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setProperty("code", code);
        return problem;
    }
}
