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

/**
 * Os serviços confiam no {@code X-Customer-Id} porque só o gateway fala com
 * eles. Por isso o valor que vem do cliente é sempre descartado e trocado pelo
 * comprador resolvido a partir do token.
 */
@Component
class CustomerIdentityFilter implements GlobalFilter, Ordered {

    static final String CUSTOMER_ID = "X-Customer-Id";

    /**
     * O comprador resolvido, para os filtros seguintes. O X-Act-As sai da
     * requisição aqui; quem resolvesse de novo veria só o token do simulator.
     */
    static final String CUSTOMER_ATTRIBUTE = CustomerIdentityFilter.class.getName() + ".customer";

    private final CustomerResolver customers;
    private final ProblemResponses problems;
    private final Telemetry telemetry;

    CustomerIdentityFilter(CustomerResolver customers, ProblemResponses problems, Telemetry telemetry) {
        this.customers = customers;
        this.problems = problems;
        this.telemetry = telemetry;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return customers.resolve(exchange)
                .map(customer -> withCustomer(exchange, customer))
                .defaultIfEmpty(withCustomer(exchange, null))
                .flatMap(chain::filter)
                .onErrorResume(ActAsForbiddenException.class, forbidden -> {
                    SalePaths.saleId(exchange).ifPresent(saleId -> telemetry.publish(saleId, "ACT_AS_FORBIDDEN",
                            exchange.getRequest().getHeaders().getFirst(CustomerResolver.ACT_AS), Map.of()));
                    return problems.write(exchange, HttpStatus.FORBIDDEN, "ACT_AS_FORBIDDEN", forbidden.getMessage());
                });
    }

    // Logo depois do UpstreamUnavailableFilter e antes do rate limiter e do
    // roteamento, para nenhum filtro ver a identidade enviada pelo cliente.
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    private static ServerWebExchange withCustomer(ServerWebExchange exchange, String customer) {
        if (customer != null) {
            exchange.getAttributes().put(CUSTOMER_ATTRIBUTE, customer);
        }
        return exchange.mutate()
                .request(request -> request.headers(headers -> {
                    headers.remove(CUSTOMER_ID);
                    headers.remove(CustomerResolver.ACT_AS);
                    if (customer != null) {
                        headers.set(CUSTOMER_ID, customer);
                    }
                }))
                .build();
    }
}
