package dev.lucaswotta.tickethit.gateway.security;

import java.util.regex.Pattern;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Decide quem é o comprador de uma requisição. Por padrão, o {@code sub} do
 * token. O simulator, e só ele, pode agir em nome de um bot com
 * {@code X-Act-As}, e só para ids com prefixo {@code bot-}: um usuário real
 * nunca pode ser personificado.
 */
@Component
public class CustomerResolver {

    public static final String ACT_AS = "X-Act-As";

    private static final Pattern BOT_ID = Pattern.compile("bot-[0-9]{1,6}");
    private static final String SIMULATOR = "ROLE_simulator";

    Mono<String> resolve(ServerWebExchange exchange) {
        return exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(token -> resolve(token, exchange.getRequest().getHeaders().getFirst(ACT_AS)));
    }

    private static String resolve(JwtAuthenticationToken token, String actAs) {
        if (actAs == null) {
            return token.getToken().getSubject();
        }
        boolean simulator = token.getAuthorities().stream().anyMatch(role -> SIMULATOR.equals(role.getAuthority()));
        if (!simulator) {
            throw new ActAsForbiddenException("só o simulator pode usar " + ACT_AS);
        }
        if (!BOT_ID.matcher(actAs).matches()) {
            throw new ActAsForbiddenException(ACT_AS + " aceita só ids de bot (bot-<número>)");
        }
        return actAs;
    }
}
