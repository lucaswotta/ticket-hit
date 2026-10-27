package dev.lucaswotta.tickethit.payment.charge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.lucaswotta.tickethit.payment.support.IntegrationTest;
import dev.lucaswotta.tickethit.payment.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

@IntegrationTest
class ChargeProcessorIT {

    private static final Duration DECISION_DELAY = Duration.ofSeconds(1);

    @Autowired
    ChargeService chargeService;

    @Autowired
    ChargeProcessor processor;

    @Autowired
    FakeGateway gateway;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcClient jdbc;

    @AfterEach
    void restoreGateway() {
        gateway.declineRate(0.1);
    }

    @Test
    void approvedChargeWritesEventInTheSameTransaction() {
        gateway.declineRate(0);
        Charge charge = newCharge();
        clock.advance(DECISION_DELAY);

        processor.processDue();

        assertThat(chargeService.find(charge.id()).status()).isEqualTo(ChargeStatus.APPROVED);
        assertThat(eventTypesFor(charge.holdId())).containsExactly("PAYMENT_APPROVED");
    }

    // O MutableClock tem nanossegundos e o timestamptz guarda microssegundos:
    // o instante do evento precisa ser o mesmo que ficou gravado na cobrança.
    @Test
    void eventCarriesTheDecisionInstantStoredInTheDatabase() {
        Charge charge = newCharge();
        clock.advance(DECISION_DELAY);

        processor.processDue();

        Instant stored = jdbc.sql("select decided_at from charge where id = :id")
                .param("id", charge.id())
                .query(OffsetDateTime.class)
                .single()
                .toInstant();
        String published = jdbc.sql("select payload ->> 'occurredAt' from outbox_event where message_key = :key")
                .param("key", charge.holdId().toString())
                .query(String.class)
                .single();
        assertThat(Instant.parse(published)).isEqualTo(stored);
    }

    @Test
    void declinedChargeWritesDeclinedEvent() {
        gateway.declineRate(1);
        Charge charge = newCharge();
        clock.advance(DECISION_DELAY);

        processor.processDue();

        assertThat(chargeService.find(charge.id()).status()).isEqualTo(ChargeStatus.DECLINED);
        assertThat(eventTypesFor(charge.holdId())).containsExactly("PAYMENT_DECLINED");
    }

    @Test
    void chargeIsNotDecidedBeforeTheDelay() {
        Charge charge = newCharge();

        processor.processDue();

        assertThat(chargeService.find(charge.id()).status()).isEqualTo(ChargeStatus.PENDING);
        assertThat(eventTypesFor(charge.holdId())).isEmpty();
    }

    // Vários processadores disputando as mesmas cobranças, como com mais de uma
    // instância do payment. Cada cobrança precisa sair com exatamente um evento.
    @Test
    void concurrentProcessorsDecideEachChargeOnce() throws Exception {
        List<UUID> holdIds = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            holdIds.add(newCharge().holdId());
        }
        clock.advance(DECISION_DELAY);

        var start = new CountDownLatch(1);
        List<Future<?>> processors = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 4; i++) {
                processors.add(executor.submit(() -> {
                    start.await();
                    while (processor.processDue() > 0) {}
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> running : processors) {
                running.get();
            }
        }

        for (UUID holdId : holdIds) {
            assertThat(eventTypesFor(holdId)).hasSize(1);
        }
    }

    private Charge newCharge() {
        UUID holdId = UUID.randomUUID();
        return chargeService.charge(holdId.toString(), holdId, "ana").charge();
    }

    private List<String> eventTypesFor(UUID holdId) {
        return jdbc.sql("select payload ->> 'type' from outbox_event where message_key = :key")
                .param("key", holdId.toString())
                .query(String.class)
                .list();
    }
}
