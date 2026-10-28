package dev.lucaswotta.tickethit.simulator.api;

import dev.lucaswotta.tickethit.simulator.SimulatorProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Fala com o gateway usando o token do simulator; o bot da vez vai no {@code X-Act-As}. */
@Component
class GatewayApi implements TicketHitApi {

    private static final String ACT_AS = "X-Act-As";

    private final RestClient rest;
    private final JsonMapper json;

    GatewayApi(SimulatorProperties properties, OAuth2AuthorizedClientManager clients, JsonMapper json) {
        var token = new OAuth2ClientHttpRequestInterceptor(clients);
        token.setClientRegistrationIdResolver(request -> "keycloak");
        token.setPrincipalResolver(request -> UsernamePasswordAuthenticationToken.unauthenticated("simulator", null));
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        var requestFactory = new JdkClientHttpRequestFactory(http);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.rest = RestClient.builder()
                .baseUrl(properties.gatewayUrl())
                .requestFactory(requestFactory)
                .requestInterceptor(token)
                .build();
        this.json = json;
    }

    @Override
    public UUID openSale(String name, int tickets, String inventoryStrategy) {
        Reply reply = send(rest.post()
                .uri("/api/sales")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name, "totalTickets", tickets, "inventoryStrategy", inventoryStrategy)));
        requireStatus(reply, HttpStatus.CREATED, "abrir venda");
        return UUID.fromString(reply.text("id"));
    }

    @Override
    public void openQueue(UUID saleId, Instant opensAt, int admissionsPerSecond) {
        Reply reply = send(rest.post()
                .uri("/api/queues")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "saleId", saleId.toString(),
                        "opensAt", opensAt.toString(),
                        "admissionsPerSecond", admissionsPerSecond)));
        requireStatus(reply, HttpStatus.CREATED, "abrir fila");
    }

    @Override
    public Reply sale(UUID saleId) {
        return send(rest.get().uri("/api/sales/{id}", saleId));
    }

    @Override
    public Reply join(UUID saleId, String bot) {
        return send(rest.post().uri("/api/queues/{id}/entries", saleId).header(ACT_AS, bot));
    }

    @Override
    public Reply entry(UUID saleId, String bot) {
        return send(rest.get().uri("/api/queues/{id}/entries/me", saleId).header(ACT_AS, bot));
    }

    @Override
    public Reply hold(UUID saleId, String bot, String pass, int quantity) {
        var request = rest.post()
                .uri("/api/sales/{id}/holds", saleId)
                .header(ACT_AS, bot)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("quantity", quantity));
        if (pass != null) {
            request = request.header("Queue-Pass", pass);
        }
        return send(request);
    }

    @Override
    public Reply pay(UUID holdId, String bot) {
        return send(rest.post().uri("/api/holds/{id}/payment", holdId).header(ACT_AS, bot));
    }

    @Override
    public Reply holdStatus(UUID holdId, String bot) {
        return send(rest.get().uri("/api/holds/{id}", holdId).header(ACT_AS, bot));
    }

    // exchange em vez de retrieve: 4xx e 5xx são respostas esperadas que o bot
    // trata pelo status, não exceções.
    private Reply send(RestClient.RequestHeadersSpec<?> request) {
        return request.exchange((sent, response) -> {
            byte[] body = response.getBody().readAllBytes();
            JsonNode parsed = body.length == 0 ? null : json.readTree(body);
            return new Reply(response.getStatusCode().value(), parsed);
        });
    }

    private static void requireStatus(Reply reply, HttpStatus expected, String action) {
        if (!reply.is(expected.value())) {
            throw new IllegalStateException("Falha ao %s: HTTP %d %s".formatted(action, reply.status(), reply.body()));
        }
    }
}
