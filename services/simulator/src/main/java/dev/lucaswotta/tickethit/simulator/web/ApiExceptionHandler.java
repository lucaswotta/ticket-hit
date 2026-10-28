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

    // Retry-After e retryAfterSeconds com o fim estimado da sala em andamento:
    // o site mostra quanto falta e tenta de novo sozinho.
    @ExceptionHandler(RoomsFullException.class)
    ResponseEntity<ProblemDetail> full(RoomsFullException exception) {
        long seconds = Math.max(1, exception.retryAfter().toSeconds());
        ProblemDetail problem = problem(HttpStatus.SERVICE_UNAVAILABLE, "ROOMS_FULL", exception);
        problem.setProperty("retryAfterSeconds", seconds);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(seconds))
                .body(problem);
    }

    private static ProblemDetail problem(HttpStatus status, String code, RuntimeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setProperty("code", code);
        return problem;
    }
}
