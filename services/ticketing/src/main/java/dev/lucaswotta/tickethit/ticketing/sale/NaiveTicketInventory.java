package dev.lucaswotta.tickethit.ticketing.sale;

import java.time.Duration;
import java.util.OptionalInt;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Errada de propósito, para o modo sem proteção da demo. Entre o SELECT e o
 * UPDATE, outras requisições leem o mesmo saldo, todas concluem que há
 * ingresso e todas subtraem.
 */
@Repository
class NaiveTicketInventory implements TicketInventory {

    private final JdbcClient jdbc;
    private final Duration readWriteGap;

    NaiveTicketInventory(JdbcClient jdbc, @Value("${ticketing.naive.read-write-gap}") Duration readWriteGap) {
        this.jdbc = jdbc;
        this.readWriteGap = readWriteGap;
    }

    @Override
    public InventoryStrategy strategy() {
        return InventoryStrategy.NAIVE;
    }

    @Override
    public OptionalInt tryWithdraw(UUID saleId, int quantity) {
        int available = jdbc.sql("select available_tickets from sale where id = :saleId")
                .param("saleId", saleId)
                .query(Integer.class)
                .single();
        if (available < quantity) {
            return OptionalInt.empty();
        }
        pause();
        int remaining = jdbc.sql("""
                        update sale
                           set available_tickets = available_tickets - :quantity
                         where id = :saleId
                     returning available_tickets
                        """)
                .param("saleId", saleId)
                .param("quantity", quantity)
                .query(Integer.class)
                .single();
        return OptionalInt.of(remaining);
    }

    private void pause() {
        try {
            Thread.sleep(readWriteGap);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
