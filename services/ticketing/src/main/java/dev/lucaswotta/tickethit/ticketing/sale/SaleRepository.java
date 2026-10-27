package dev.lucaswotta.tickethit.ticketing.sale;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SaleRepository {

    private final JdbcClient jdbc;

    SaleRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UUID open(String name, int totalTickets, InventoryStrategy strategy) {
        return jdbc.sql("""
                        insert into sale (name, total_tickets, available_tickets, inventory_strategy)
                        values (:name, :total, :total, :strategy)
                        returning id
                        """)
                .param("name", name)
                .param("total", totalTickets)
                .param("strategy", strategy.name())
                .query(UUID.class)
                .single();
    }

    public Optional<Sale> find(UUID saleId) {
        return jdbc.sql("""
                        select id, name, total_tickets, available_tickets, inventory_strategy
                          from sale
                         where id = :saleId
                        """)
                .param("saleId", saleId)
                .query(Sale.class)
                .optional();
    }

    public int availableTickets(UUID saleId) {
        return jdbc.sql("select available_tickets from sale where id = :saleId")
                .param("saleId", saleId)
                .query(Integer.class)
                .single();
    }
}
