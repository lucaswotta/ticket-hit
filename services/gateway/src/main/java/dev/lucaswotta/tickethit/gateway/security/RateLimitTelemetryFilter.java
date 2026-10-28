package dev.lucaswotta.tickethit.gateway.security;

import dev.lucaswotta.tickethit.gateway.telemetry.Telemetry;
import java.util.Map;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Conta no raio-x cada requisição barrada pelo rate limit, para a enxurrada dos bots aparecer. */
@Component
class RateLimitTelemetryFilter implements GlobalFilter, Ordered {

    private final Telemetry telemetry;

    RateLimitTelemetryFilter(Telemetry telemetry) {
        this.telemetry = telemetry;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            if (exchange.getResponse().getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                SalePaths.saleId(exchange).ifPresent(saleId -> telemetry.publish(saleId, "RATE_LIMITED",
                        exchange.getAttribute(CustomerIdentityFilter.CUSTOMER_ATTRIBUTE),
                        Map.of("path", exchange.getRequest().getPath().value())));
            }
        }));
    }

    // Logo depois do filtro de identidade, para envolver o rate limiter da rota.
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }
}
