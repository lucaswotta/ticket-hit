package dev.lucaswotta.tickethit.simulator.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

class SingleFlightAuthorizedClientManagerTest {

    // O falso imita o manager do Spring: se não há token guardado, "vai ao
    // Keycloak" (conta, demora) e guarda. Sem a trava, 200 threads no instante
    // do vencimento fariam vários pedidos; com ela, um.
    @Test
    void concurrentCallersShareOneTokenRequest() throws Exception {
        var tokenRequests = new AtomicInteger();
        var stored = new AtomicReference<OAuth2AuthorizedClient>();
        OAuth2AuthorizedClientManager springLike = request -> {
            if (stored.get() == null) {
                tokenRequests.incrementAndGet();
                sleep(50);
                stored.set(mock(OAuth2AuthorizedClient.class));
            }
            return stored.get();
        };
        var manager = new SingleFlightAuthorizedClientManager(springLike);
        var request = OAuth2AuthorizeRequest.withClientRegistrationId("keycloak").principal("simulator").build();

        var start = new CountDownLatch(1);
        List<Future<OAuth2AuthorizedClient>> callers = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 200; i++) {
                callers.add(executor.submit(() -> {
                    start.await();
                    return manager.authorize(request);
                }));
            }
            start.countDown();
            for (Future<OAuth2AuthorizedClient> caller : callers) {
                assertThat(caller.get()).isSameAs(stored.get());
            }
        }

        assertThat(tokenRequests).hasValue(1);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
