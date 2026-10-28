package dev.lucaswotta.tickethit.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.JwtMutator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Gateway real na frente de um serviço falso (HttpServer da JDK) que guarda a
 * última requisição recebida. O token é montado pelo spring-security-test;
 * a validação contra o Keycloak de verdade fica no teste de ponta a ponta.
 */
@SpringBootTest(properties = {"gateway.rate-limit.customer.per-second=1", "gateway.rate-limit.customer.burst=3"})
@AutoConfigureWebTestClient
@Import(TestRedis.class)
class GatewayIT {

    private static final AtomicReference<Received> lastRequest = new AtomicReference<>();
    private static final AtomicInteger received = new AtomicInteger();
    private static final HttpServer upstream = startUpstream();

    @Autowired
    WebTestClient client;

    @Autowired
    KafkaConnectionDetails kafka;

    @DynamicPropertySource
    static void routesToUpstream(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + upstream.getAddress().getPort();
        registry.add("QUEUE_URI", () -> url);
        registry.add("TICKETING_URI", () -> url);
        registry.add("SIMULATOR_URI", () -> url);
        registry.add("XRAY_URI", () -> url);
    }

    @AfterAll
    static void stopUpstream() {
        upstream.stop(0);
    }

    @BeforeEach
    void forgetLastRequest() {
        lastRequest.set(null);
        received.set(0);
    }

    @Test
    void publicSaleInfoNeedsNoTokenAndDropsSpoofedCustomer() {
        client.get()
                .uri("/api/sales/{id}", UUID.randomUUID())
                .header("X-Customer-Id", "bob")
                .exchange()
                .expectStatus()
                .isOk();

        assertThat(lastRequest.get().headers().containsKey("X-customer-id")).isFalse();
    }

    @Test
    void buyerRoutesRequireAToken() {
        client.post().uri("/api/holds/{id}/payment", UUID.randomUUID()).exchange().expectStatus().isUnauthorized();

        assertThat(lastRequest.get()).isNull();
    }

    @Test
    void customerComesFromTheTokenAndSpoofedHeaderIsReplaced() {
        client.mutateWith(user("ana"))
                .post()
                .uri("/api/holds/{id}/payment", UUID.randomUUID())
                .header("X-Customer-Id", "bob")
                .exchange()
                .expectStatus()
                .isOk();

        assertThat(lastRequest.get().headers().get("X-customer-id")).containsExactly("ana");
    }

    @Test
    void simulatorActsAsABot() {
        client.mutateWith(simulator())
                .post()
                .uri("/api/queues/{id}/entries", UUID.randomUUID())
                .header("X-Act-As", "bot-42")
                .exchange()
                .expectStatus()
                .isOk();

        assertThat(lastRequest.get().headers().get("X-customer-id")).containsExactly("bot-42");
        assertThat(lastRequest.get().headers().containsKey("X-act-as")).isFalse();
    }

    @Test
    void regularUserCannotActAsAnyone() {
        client.mutateWith(user("ana"))
                .post()
                .uri("/api/queues/{id}/entries", UUID.randomUUID())
                .header("X-Act-As", "bot-1")
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("ACT_AS_FORBIDDEN");

        assertThat(lastRequest.get()).isNull();
    }

    @Test
    void simulatorCannotActAsARealUser() {
        client.mutateWith(simulator())
                .post()
                .uri("/api/queues/{id}/entries", UUID.randomUUID())
                .header("X-Act-As", "ana")
                .exchange()
                .expectStatus()
                .isForbidden();

        assertThat(lastRequest.get()).isNull();
    }

    @Test
    void openingSalesAndQueuesNeedsTheSimulatorRole() {
        client.mutateWith(user("ana")).post().uri("/api/sales").exchange().expectStatus().isForbidden();
        client.mutateWith(user("ana")).post().uri("/api/queues").exchange().expectStatus().isForbidden();

        client.mutateWith(simulator()).post().uri("/api/sales").exchange().expectStatus().isOk();
    }

    @Test
    void demoRoomsNeedALoggedVisitor() {
        client.post().uri("/api/rooms").exchange().expectStatus().isUnauthorized();

        client.mutateWith(user("ana")).post().uri("/api/rooms").exchange().expectStatus().isOk();

        assertThat(lastRequest.get().path()).isEqualTo("/api/rooms");
        assertThat(lastRequest.get().headers().get("X-customer-id")).containsExactly("ana");
    }

    // O retry do gateway é só para falha de conexão: um 503 do serviço é uma
    // resposta e volta como veio, com um único pedido.
    @Test
    void serviceErrorResponsesAreNotRetried() {
        client.get()
                .uri("/api/sales/{id}", UUID.randomUUID())
                .header("X-Fake-Status", "503")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        assertThat(received).hasValue(1);
    }

    @Test
    void xrayNeedsALoggedVisitor() {
        client.get().uri("/api/xray/{id}/events", UUID.randomUUID()).exchange().expectStatus().isUnauthorized();

        client.mutateWith(user("ana")).get().uri("/api/xray/{id}/events", UUID.randomUUID()).exchange()
                .expectStatus().isOk();

        assertThat(lastRequest.get().path()).startsWith("/api/xray/");
    }

    @Test
    void paymentServiceIsNotExposed() {
        client.mutateWith(user("ana")).post().uri("/api/charges").exchange().expectStatus().isNotFound();
        client.get().uri("/actuator/health").exchange().expectStatus().isUnauthorized();

        assertThat(lastRequest.get()).isNull();
    }

    @Test
    void eachCustomerHasItsOwnRateLimit() {
        String customer = "ana-" + UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            client.mutateWith(user(customer)).get().uri("/api/holds/x").exchange().expectStatus().isOk();
        }

        client.mutateWith(user(customer))
                .get()
                .uri("/api/holds/x")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        client.mutateWith(user("bia-" + UUID.randomUUID()))
                .get()
                .uri("/api/holds/x")
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void blockedRequestsShowUpInTelemetry() {
        UUID saleId = UUID.randomUUID();
        for (int i = 0; i < 4; i++) {
            client.mutateWith(simulator()).get().uri("/api/queues/{id}/entries/me", saleId)
                    .header("X-Act-As", "bot-77").exchange();
        }
        client.mutateWith(user("ana")).post().uri("/api/queues/{id}/entries", saleId)
                .header("X-Act-As", "bot-1").exchange();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(
                        TelemetryReader.eventsOf(kafka.getBootstrapServers(), saleId))
                .extracting(event -> event.get("type").asString())
                .containsExactlyInAnyOrder("RATE_LIMITED", "ACT_AS_FORBIDDEN"));
    }

    // Todos os bots usam o mesmo token do simulator; o balde precisa ser do bot.
    @Test
    void eachBotHasItsOwnRateLimit() {
        JwtMutator simulator = simulator();
        for (int i = 0; i < 3; i++) {
            client.mutateWith(simulator).get().uri("/api/holds/x").header("X-Act-As", "bot-1").exchange()
                    .expectStatus().isOk();
        }

        client.mutateWith(simulator).get().uri("/api/holds/x").header("X-Act-As", "bot-1").exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        client.mutateWith(simulator).get().uri("/api/holds/x").header("X-Act-As", "bot-2").exchange()
                .expectStatus().isOk();
    }

    private static JwtMutator user(String subject) {
        return mockJwt().jwt(jwt -> jwt.subject(subject));
    }

    private static JwtMutator simulator() {
        return mockJwt().jwt(jwt -> jwt.subject(UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_simulator"));
    }

    private static HttpServer startUpstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                lastRequest.set(new Received(exchange.getRequestURI().getPath(), exchange.getRequestHeaders()));
                received.incrementAndGet();
                // O teste escolhe o status pelo header; sem ele, 200.
                String status = exchange.getRequestHeaders().getFirst("X-Fake-Status");
                byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status == null ? 200 : Integer.parseInt(status), body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // HttpServer normaliza os nomes de header: primeira letra maiúscula, resto minúsculo.
    record Received(String path, Headers headers) {}
}
