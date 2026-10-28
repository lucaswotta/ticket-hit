package dev.lucaswotta.tickethit.ticketing.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.lucaswotta.tickethit.ticketing.hold.Hold;
import dev.lucaswotta.tickethit.ticketing.hold.HoldService;
import dev.lucaswotta.tickethit.ticketing.hold.SoldOutException;
import dev.lucaswotta.tickethit.ticketing.sale.InventoryStrategy;
import dev.lucaswotta.tickethit.ticketing.sale.SaleRepository;
import dev.lucaswotta.tickethit.ticketing.support.IntegrationTest;
import dev.lucaswotta.tickethit.ticketing.support.MutableClock;
import dev.lucaswotta.tickethit.ticketing.support.TelemetryReader;
import dev.lucaswotta.tickethit.ticketing.support.TestPasses;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

@IntegrationTest
class TelemetryIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @Autowired
    HoldService holdService;

    @Autowired
    SaleRepository sales;

    @Autowired
    KafkaConnectionDetails kafka;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    MutableClock clock;

    @Autowired
    TransactionTemplate transactions;

    @Test
    void purchaseShowsUpAsEventsWithTheRemainingStock() {
        UUID saleId = sales.open("Noite 1", 3, InventoryStrategy.ATOMIC);
        Hold hold = holdService.hold(saleId, "bot-1", 2, UUID.randomUUID());
        holdService.confirm(hold.id());

        await().atMost(TIMEOUT).untilAsserted(() -> {
            List<JsonNode> events = events(saleId);
            assertThat(events).extracting(event -> event.get("type").asString())
                    .containsExactly("HOLD_CREATED", "HOLD_CONFIRMED");
            assertThat(events.getFirst().get("data").get("available").asInt()).isEqualTo(1);
            assertThat(events.getFirst().get("customerId").asString()).isEqualTo("bot-1");
            assertThat(events.get(1).get("data").get("ticket").asInt()).isEqualTo(2);
        });
    }

    // O raio-x do modo sem proteção depende disso: o estoque informado no
    // evento é o que ficou gravado, inclusive abaixo de zero.
    @Test
    void naiveOversellShowsNegativeStock() throws Exception {
        UUID saleId = sales.open("Noite 1", 5, InventoryStrategy.NAIVE);

        var start = new CountDownLatch(1);
        List<Future<?>> buyers = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 20; i++) {
                String bot = "bot-" + i;
                buyers.add(executor.submit(() -> {
                    start.await();
                    try {
                        holdService.hold(saleId, bot, 1, UUID.randomUUID());
                    } catch (SoldOutException soldOut) {
                        // parte dos compradores vê o estoque acabar
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> buyer : buyers) {
                buyer.get();
            }
        }

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(events(saleId))
                .extracting(event -> event.get("data").get("available").asInt())
                .anySatisfy(available -> assertThat(available).isNegative()));
    }

    @Test
    void rejectedHoldShowsUpWithItsCode() {
        UUID saleId = sales.open("Noite 1", 1, InventoryStrategy.ATOMIC);
        String pass = TestPasses.issue(saleId, "ana", clock.instant(), clock.instant().plusSeconds(120));

        mvc.post()
                .uri("/api/sales/{id}/holds", saleId)
                .header("X-Customer-Id", "bot-9")
                .header("Queue-Pass", pass)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\": 1}")
                .exchange();

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(events(saleId)).singleElement().satisfies(event -> {
            assertThat(event.get("type").asString()).isEqualTo("HOLD_REJECTED");
            assertThat(event.get("data").get("code").asString()).isEqualTo("INVALID_PASS");
        }));
    }

    @Test
    void rolledBackHoldPublishesNothing() {
        UUID saleId = sales.open("Noite 1", 5, InventoryStrategy.ATOMIC);

        transactions.executeWithoutResult(status -> {
            holdService.hold(saleId, "bot-1", 1, UUID.randomUUID());
            status.setRollbackOnly();
        });
        holdService.hold(saleId, "bot-2", 1, UUID.randomUUID());

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(events(saleId))
                .extracting(event -> event.get("customerId").asString())
                .containsExactly("bot-2"));
    }

    private List<JsonNode> events(UUID saleId) {
        return TelemetryReader.eventsOf(kafka.getBootstrapServers(), saleId);
    }
}
