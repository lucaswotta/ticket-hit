package dev.lucaswotta.tickethit.payment.charge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.lucaswotta.tickethit.payment.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

@IntegrationTest
class ChargeServiceIT {

    @Autowired
    ChargeService chargeService;

    @Autowired
    JdbcClient jdbc;

    @Test
    void sameKeyReturnsTheSameCharge() {
        UUID holdId = UUID.randomUUID();

        ChargeResult first = chargeService.charge(holdId.toString(), holdId, "ana");
        ChargeResult second = chargeService.charge(holdId.toString(), holdId, "ana");

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.charge().id()).isEqualTo(first.charge().id());
        assertThat(chargesWithKey(holdId.toString())).isEqualTo(1);
    }

    @Test
    void concurrentRequestsWithSameKeyCreateOneCharge() throws Exception {
        UUID holdId = UUID.randomUUID();
        var start = new CountDownLatch(1);
        List<Future<UUID>> attempts = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 20; i++) {
                attempts.add(executor.submit(() -> {
                    start.await();
                    return chargeService.charge(holdId.toString(), holdId, "ana").charge().id();
                }));
            }
            start.countDown();
            List<UUID> ids = new ArrayList<>();
            for (Future<UUID> attempt : attempts) {
                ids.add(attempt.get());
            }
            assertThat(ids).containsOnly(ids.getFirst());
        }
        assertThat(chargesWithKey(holdId.toString())).isEqualTo(1);
    }

    @Test
    void keyReusedForAnotherHoldIsRejected() {
        String key = UUID.randomUUID().toString();
        chargeService.charge(key, UUID.randomUUID(), "ana");

        assertThatThrownBy(() -> chargeService.charge(key, UUID.randomUUID(), "ana"))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    private int chargesWithKey(String key) {
        return jdbc.sql("select count(*) from charge where idempotency_key = :key")
                .param("key", key)
                .query(Integer.class)
                .single();
    }
}
