package dev.lucaswotta.tickethit.ticketing.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;

/** Payment falso com o HttpServer da JDK: cada teste decide a resposta de cada tentativa. */
class PaymentClientTest {

    private static final Duration READ_TIMEOUT = Duration.ofMillis(300);

    private final List<String> idempotencyKeys = new CopyOnWriteArrayList<>();
    private final AtomicInteger attempts = new AtomicInteger();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void retriesServerErrorsWithTheSameIdempotencyKey() {
        PaymentClient client = clientRespondingWith(attempt -> attempt < 3 ? 503 : 202);
        UUID holdId = UUID.randomUUID();

        client.charge(holdId, "ana");

        assertThat(attempts).hasValue(3);
        assertThat(idempotencyKeys).containsOnly(holdId.toString()).hasSize(3);
    }

    @Test
    void retriesAfterReadTimeout() {
        PaymentClient client = clientRespondingWith(attempt -> {
            if (attempt == 1) {
                sleep(READ_TIMEOUT.multipliedBy(3));
            }
            return 202;
        });

        client.charge(UUID.randomUUID(), "ana");

        assertThat(attempts).hasValue(2);
    }

    @Test
    void givesUpAfterMaxRetries() {
        PaymentClient client = clientRespondingWith(attempt -> 503);

        assertThatThrownBy(() -> client.charge(UUID.randomUUID(), "ana")).isInstanceOf(PaymentUnavailableException.class);

        assertThat(attempts).hasValue(4);
    }

    @Test
    void doesNotRetryClientErrors() {
        PaymentClient client = clientRespondingWith(attempt -> 422);

        assertThatThrownBy(() -> client.charge(UUID.randomUUID(), "ana")).isInstanceOf(HttpClientErrorException.class);

        assertThat(attempts).hasValue(1);
    }

    private PaymentClient clientRespondingWith(IntFunction<Integer> statusForAttempt) {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/api/charges", exchange -> respond(exchange, statusForAttempt));
        // Uma thread por requisição: com a thread única padrão, a tentativa que
        // dorme seguraria a próxima na fila e o retry também estouraria o timeout.
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        var properties = new PaymentClientProperties(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                Duration.ofSeconds(1),
                READ_TIMEOUT,
                3,
                Duration.ofMillis(10));
        return new PaymentClient(properties);
    }

    private void respond(HttpExchange exchange, IntFunction<Integer> statusForAttempt) throws IOException {
        idempotencyKeys.add(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
        int status = statusForAttempt.apply(attempts.incrementAndGet());
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
