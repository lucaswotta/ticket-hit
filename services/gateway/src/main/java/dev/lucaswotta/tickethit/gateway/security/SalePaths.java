package dev.lucaswotta.tickethit.gateway.security;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.web.server.ServerWebExchange;

/** A venda de uma requisição, quando o caminho a traz; é a chave dos eventos do raio-x. */
final class SalePaths {

    private static final Pattern SALE_PATH =
            Pattern.compile("^/api/(?:sales|queues)/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(?:/.*)?$");

    private SalePaths() {}

    static Optional<UUID> saleId(ServerWebExchange exchange) {
        var matcher = SALE_PATH.matcher(exchange.getRequest().getPath().value());
        return matcher.matches() ? Optional.of(UUID.fromString(matcher.group(1))) : Optional.empty();
    }
}
