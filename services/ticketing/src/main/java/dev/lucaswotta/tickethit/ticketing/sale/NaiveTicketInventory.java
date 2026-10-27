package dev.lucaswotta.tickethit.ticketing.sale;

import java.util.UUID;
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

    NaiveTicketInventory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public InventoryStrategy strategy() {
        return InventoryStrategy.NAIVE;
    }

    @Override
    public boolean tryWithdraw(UUID saleId, int quantity) {
        int available = jdbc.sql("select available_tickets from sale where id = :saleId")
                .param("saleId", saleId)
                .query(Integer.class)
                .single();
        if (available < quantity) {
            return false;
        }
        jdbc.sql("update sale set available_tickets = available_tickets - :quantity where id = :saleId")
                .param("saleId", saleId)
                .param("quantity", quantity)
                .update();
        return true;
    }
}
