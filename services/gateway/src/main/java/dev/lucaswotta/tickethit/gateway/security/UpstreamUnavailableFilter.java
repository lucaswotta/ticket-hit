package dev.lucaswotta.tickethit.gateway.security;

import java.net.ConnectException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Serviço fora do ar vira 503, não 500: o cliente e os bots sabem que podem
 * tentar de novo, e o botão de caos mostra a queda como queda.
 */
@Component
class UpstreamUnavailableFilter implements GlobalFilter, Ordered {

    private final ProblemResponses problems;

    UpstreamUnavailableFilter(ProblemResponses problems) {
        this.problems = problems;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange)
                .onErrorResume(ConnectException.class, refused -> problems.write(
                        exchange, HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_UNAVAILABLE", "Serviço indisponível"));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
