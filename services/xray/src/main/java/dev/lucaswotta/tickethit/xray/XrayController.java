package dev.lucaswotta.tickethit.xray;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
class XrayController {

    private final XrayHub hub;

    XrayController(XrayHub hub) {
        this.hub = hub;
    }

    @GetMapping(path = "/api/xray/{saleId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(@PathVariable UUID saleId) {
        return hub.subscribe(saleId);
    }

    @ExceptionHandler(TooManyStreamsException.class)
    ProblemDetail tooManyStreams(TooManyStreamsException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
        problem.setProperty("code", "TOO_MANY_STREAMS");
        return problem;
    }
}
