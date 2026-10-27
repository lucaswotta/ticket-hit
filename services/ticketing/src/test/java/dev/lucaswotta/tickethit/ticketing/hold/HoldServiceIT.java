package dev.lucaswotta.tickethit.ticketing.hold;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.lucaswotta.tickethit.ticketing.sale.InventoryStrategy;
import dev.lucaswotta.tickethit.ticketing.sale.SaleNotFoundException;
import dev.lucaswotta.tickethit.ticketing.sale.SaleRepository;
import dev.lucaswotta.tickethit.ticketing.support.IntegrationTest;
import dev.lucaswotta.tickethit.ticketing.support.MutableClock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

@IntegrationTest
class HoldServiceIT {

    private static final Duration HOLD_DURATION = Duration.ofMinutes(10);

    @Autowired
    HoldService holdService;

    @Autowired
    SaleRepository sales;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcClient jdbc;

    @Test
    void holdWithdrawsTicketsAndExpiresInTenMinutes() {
        UUID saleId = sales.open("Noite 1", 10, InventoryStrategy.ATOMIC);

        Hold hold = holdService.hold(saleId, "ana", 3, UUID.randomUUID());

        assertThat(hold.status()).isEqualTo(HoldStatus.HELD);
        assertThat(hold.expiresAt()).isCloseTo(clock.instant().plus(HOLD_DURATION), within(1, ChronoUnit.MILLIS));
        assertThat(holdService.find(hold.id())).isEqualTo(hold);
        assertThat(sales.availableTickets(saleId)).isEqualTo(7);
    }

    @Test
    void soldOutCreatesNoHold() {
        UUID saleId = sales.open("Noite 1", 2, InventoryStrategy.ATOMIC);
        holdService.hold(saleId, "ana", 2, UUID.randomUUID());

        assertThatThrownBy(() -> holdService.hold(saleId, "bia", 1, UUID.randomUUID())).isInstanceOf(SoldOutException.class);

        assertThat(holdsOf(saleId)).isEqualTo(1);
        assertThat(sales.availableTickets(saleId)).isZero();
    }

    @Test
    void quantityOutsideLimitIsRejectedBeforeTouchingStock() {
        UUID saleId = sales.open("Noite 1", 10, InventoryStrategy.ATOMIC);

        assertThatThrownBy(() -> holdService.hold(saleId, "ana", 5, UUID.randomUUID())).isInstanceOf(InvalidQuantityException.class);
        assertThatThrownBy(() -> holdService.hold(saleId, "ana", 0, UUID.randomUUID())).isInstanceOf(InvalidQuantityException.class);

        assertThat(sales.availableTickets(saleId)).isEqualTo(10);
    }

    @Test
    void passAlreadyUsedIsRejectedBeforeTouchingStock() {
        UUID saleId = sales.open("Noite 1", 10, InventoryStrategy.ATOMIC);
        UUID passId = UUID.randomUUID();
        holdService.hold(saleId, "ana", 2, passId);

        assertThatThrownBy(() -> holdService.hold(saleId, "ana", 2, passId))
                .isInstanceOf(PassAlreadyUsedException.class);

        assertThat(sales.availableTickets(saleId)).isEqualTo(8);
    }

    // O mesmo passe em requisições simultâneas, como um bot disparando em
    // paralelo. Todas passam pela conferência antes do INSERT; a constraint
    // unique deixa só uma criar o hold, e as outras devolvem o estoque.
    @Test
    void concurrentHoldsWithTheSamePassCreateOne() throws Exception {
        UUID saleId = sales.open("Noite 1", 100, InventoryStrategy.ATOMIC);
        UUID passId = UUID.randomUUID();

        runConcurrently(20, () -> {
            try {
                holdService.hold(saleId, "bot", 2, passId);
                return true;
            } catch (PassAlreadyUsedException rejected) {
                return false;
            }
        });

        assertThat(holdsOf(saleId)).isEqualTo(1);
        assertThat(sales.availableTickets(saleId)).isEqualTo(98);
    }

    @Test
    void unknownSaleIsRejected() {
        assertThatThrownBy(() -> holdService.hold(UUID.randomUUID(), "ana", 1, UUID.randomUUID()))
                .isInstanceOf(SaleNotFoundException.class);
    }

    @Test
    void expiredHoldReturnsTicketsAndActiveHoldStays() {
        UUID saleId = sales.open("Noite 1", 10, InventoryStrategy.ATOMIC);
        Hold due = holdService.hold(saleId, "ana", 3, UUID.randomUUID());
        clock.advance(Duration.ofMinutes(5));
        Hold active = holdService.hold(saleId, "bia", 2, UUID.randomUUID());
        clock.advance(Duration.ofMinutes(5));

        expireAllDue();

        assertThat(holdService.find(due.id()).status()).isEqualTo(HoldStatus.EXPIRED);
        assertThat(holdService.find(active.id()).status()).isEqualTo(HoldStatus.HELD);
        assertThat(sales.availableTickets(saleId)).isEqualTo(8);
    }

    // Vários jobs disputando os mesmos holds vencidos, como acontece com mais de
    // uma instância do serviço. Se algum hold fosse devolvido duas vezes, o
    // estoque passaria do total e a constraint da tabela sale recusaria.
    @Test
    void concurrentExpirationReturnsEachHoldOnce() throws Exception {
        UUID saleId = sales.open("Noite 1", 400, InventoryStrategy.ATOMIC);
        for (int i = 0; i < 200; i++) {
            holdService.hold(saleId, "comprador-" + i, 2, UUID.randomUUID());
        }
        clock.advance(HOLD_DURATION);

        runConcurrently(8, () -> {
            while (holdService.expireDue() > 0) {}
            return true;
        });

        assertThat(sales.availableTickets(saleId)).isEqualTo(400);
        assertThat(holdsOf(saleId, HoldStatus.EXPIRED)).isEqualTo(200);
    }

    @Test
    void atomicStrategyCreatesExactlyOneHoldPerTicket() throws Exception {
        UUID saleId = sales.open("Noite 1", 500, InventoryStrategy.ATOMIC);

        runConcurrently(1_000, () -> tryHold(saleId));

        assertThat(holdsOf(saleId)).isEqualTo(500);
        assertThat(sales.availableTickets(saleId)).isZero();
    }

    // A transação do HoldService não conserta a estratégia ingênua: no READ
    // COMMITTED, o UPDATE relativo espera o lock e subtrai do saldo já gravado.
    @Test
    void naiveStrategyOversellsEvenInsideTransaction() throws Exception {
        UUID saleId = sales.open("Noite 1", 500, InventoryStrategy.NAIVE);

        runConcurrently(1_000, () -> tryHold(saleId));

        assertThat(holdsOf(saleId)).isGreaterThan(500);
        assertThat(sales.availableTickets(saleId)).isNegative();
    }

    private boolean tryHold(UUID saleId) {
        try {
            holdService.hold(saleId, "comprador", 1, UUID.randomUUID());
            return true;
        } catch (SoldOutException soldOut) {
            return false;
        }
    }

    private int holdsOf(UUID saleId) {
        return jdbc.sql("select count(*) from hold where sale_id = :saleId")
                .param("saleId", saleId)
                .query(Integer.class)
                .single();
    }

    private int holdsOf(UUID saleId, HoldStatus status) {
        return jdbc.sql("select count(*) from hold where sale_id = :saleId and status = :status")
                .param("saleId", saleId)
                .param("status", status.name())
                .query(Integer.class)
                .single();
    }

    private static void runConcurrently(int tasks, Callable<Boolean> task) throws Exception {
        var start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < tasks; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            for (Future<Boolean> future : futures) {
                future.get();
            }
        }
    }

    // Os testes dividem banco e relógio, então pode haver mais holds vencidos
    // que um lote do job; expira lote a lote até não sobrar nenhum.
    private void expireAllDue() {
        while (holdService.expireDue() > 0) {}
    }
}
