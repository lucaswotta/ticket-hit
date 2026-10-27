package dev.lucaswotta.tickethit.queue.web;

import dev.lucaswotta.tickethit.queue.room.NotInQueueException;
import dev.lucaswotta.tickethit.queue.room.QueueAlreadyExistsException;
import dev.lucaswotta.tickethit.queue.room.QueueNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Erros em Problem Details (RFC 9457), com o campo {@code code} estável. */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(QueueNotFoundException.class)
    ProblemDetail queueNotFound(QueueNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "QUEUE_NOT_FOUND", exception);
    }

    @ExceptionHandler(NotInQueueException.class)
    ProblemDetail notInQueue(NotInQueueException exception) {
        return problem(HttpStatus.NOT_FOUND, "NOT_IN_QUEUE", exception);
    }

    @ExceptionHandler(QueueAlreadyExistsException.class)
    ProblemDetail alreadyExists(QueueAlreadyExistsException exception) {
        return problem(HttpStatus.CONFLICT, "QUEUE_ALREADY_EXISTS", exception);
    }

    private static ProblemDetail problem(HttpStatus status, String code, RuntimeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setProperty("code", code);
        return problem;
    }
}
