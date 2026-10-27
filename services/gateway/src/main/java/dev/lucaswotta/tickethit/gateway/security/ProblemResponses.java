package dev.lucaswotta.tickethit.gateway.security;

import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/** Respostas de erro do próprio gateway no mesmo formato dos serviços (RFC 9457, com {@code code}). */
@Component
class ProblemResponses {

    private final JsonMapper json;

    ProblemResponses(JsonMapper json) {
        this.json = json;
    }

    Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        byte[] body = json.writeValueAsString(problem).getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}
