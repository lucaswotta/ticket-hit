package dev.lucaswotta.tickethit.ticketing.sale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import dev.lucaswotta.tickethit.ticketing.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@IntegrationTest
class TicketInventoryIT {

    private static final int TICKETS = 500;
    private static final int BUYERS = 1_000;

    @Autowired
    SaleRepository sales;

    @Autowired
    AtomicTicketInventory atomic;

    @Autowired
    NaiveTicketInventory naive;

    @Autowired
    JdbcClient jdbc;

    @Test
    void atomicStrategySellsExactlyTheStock() throws Exception {
        UUID saleId = sales.open("Noite 1", TICKETS, InventoryStrategy.ATOMIC);

        int sold = buyOneTicketEach(atomic, saleId);

        assertThat(sold).isEqualTo(TICKETS);
        assertThat(sales.availableTickets(saleId)).isZero();
    }

    @Test
    void naiveStrategyOversellsUnderConcurrency() throws Exception {
        UUID saleId = sales.open("Noite 1", TICKETS, InventoryStrategy.NAIVE);

        int sold = buyOneTicketEach(naive, saleId);

        assertThat(sold).isGreaterThan(TICKETS);
        assertThat(sales.availableTickets(saleId)).isEqualTo(TICKETS - sold);
    }

    @Test
    void databaseRejectsNegativeStockWhenProtected() {
        UUID saleId = sales.open("Noite 1", TICKETS, InventoryStrategy.ATOMIC);

        assertThatThrownBy(() -> setAvailableTickets(saleId, -1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("available_tickets_within_total");
    }

    @Test
    void databaseAllowsNegativeStockInNaiveMode() {
        UUID saleId = sales.open("Noite 1", TICKETS, InventoryStrategy.NAIVE);

        setAvailableTickets(saleId, -1);

        assertThat(sales.availableTickets(saleId)).isEqualTo(-1);
    }

    private void setAvailableTickets(UUID saleId, int available) {
        jdbc.sql("update sale set available_tickets = :available where id = :saleId")
                .param("available", available)
                .param("saleId", saleId)
                .update();
    }

    // O latch segura todos os compradores até o último estar pronto, para que
    // as requisições cheguem ao banco ao mesmo tempo.
    private int buyOneTicketEach(TicketInventory inventory, UUID saleId) throws Exception {
        var start = new CountDownLatch(1);
        List<Future<Boolean>> attempts = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < BUYERS; i++) {
                attempts.add(executor.submit(() -> {
                    start.await();
                    return inventory.tryWithdraw(saleId, 1).isPresent();
                }));
            }
            start.countDown();
            int sold = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get()) {
                    sold++;
                }
            }
            return sold;
        }
    }
}
