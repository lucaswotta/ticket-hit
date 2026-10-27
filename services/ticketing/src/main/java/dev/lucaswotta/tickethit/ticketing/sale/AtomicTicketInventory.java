package dev.lucaswotta.tickethit.ticketing.sale;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class AtomicTicketInventory implements TicketInventory {

    private final JdbcClient jdbc;

    AtomicTicketInventory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public InventoryStrategy strategy() {
        return InventoryStrategy.ATOMIC;
    }

    // Conferir e subtrair numa instrução só: o PostgreSQL trava a linha durante
    // o UPDATE, então não existe intervalo entre ler o saldo e gravar o novo.
    @Override
    public boolean tryWithdraw(UUID saleId, int quantity) {
        int updated = jdbc.sql("""
                        update sale
                           set available_tickets = available_tickets - :quantity
                         where id = :saleId
                           and available_tickets >= :quantity
                        """)
                .param("saleId", saleId)
                .param("quantity", quantity)
                .update();
        return updated == 1;
    }
}
