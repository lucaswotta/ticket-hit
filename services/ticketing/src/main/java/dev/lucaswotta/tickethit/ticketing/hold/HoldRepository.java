package dev.lucaswotta.tickethit.ticketing.hold;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class HoldRepository {

    private final JdbcClient jdbc;

    HoldRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // Devolve o que o banco gravou: o timestamptz guarda microssegundos, e o
    // Instant do Java pode ter nanossegundos.
    Hold create(UUID saleId, String customerId, int quantity, Instant expiresAt) {
        return jdbc.sql("""
                        insert into hold (sale_id, customer_id, quantity, status, expires_at)
                        values (:saleId, :customerId, :quantity, 'HELD', :expiresAt)
                        returning id, sale_id, customer_id, quantity, status, expires_at
                        """)
                .param("saleId", saleId)
                .param("customerId", customerId)
                .param("quantity", quantity)
                .param("expiresAt", utc(expiresAt))
                .query(HoldRepository::mapHold)
                .single();
    }

    Optional<Hold> find(UUID holdId) {
        return jdbc.sql("""
                        select id, sale_id, customer_id, quantity, status, expires_at
                          from hold
                         where id = :holdId
                        """)
                .param("holdId", holdId)
                .query(HoldRepository::mapHold)
                .optional();
    }

    /**
     * Expira até {@code batchSize} holds vencidos e devolve os ingressos ao estoque,
     * numa instrução só. Com SKIP LOCKED, instâncias rodando o job ao mesmo tempo
     * pegam holds diferentes, e nenhum hold devolve ingresso duas vezes.
     */
    int expireDue(Instant now, int batchSize) {
        return jdbc.sql("""
                        with due as (
                            select id, sale_id, quantity
                              from hold
                             where status = 'HELD'
                               and expires_at <= :now
                             order by expires_at
                             limit :batchSize
                               for update skip locked
                        ),
                        expired as (
                            update hold
                               set status = 'EXPIRED'
                              from due
                             where hold.id = due.id
                         returning due.sale_id, due.quantity
                        ),
                        returned as (
                            update sale
                               set available_tickets = available_tickets + per_sale.quantity
                              from (select sale_id, sum(quantity) as quantity
                                      from expired
                                     group by sale_id) per_sale
                             where sale.id = per_sale.sale_id
                        )
                        select count(*) from expired
                        """)
                .param("now", utc(now))
                .param("batchSize", batchSize)
                .query(Integer.class)
                .single();
    }

    private static Hold mapHold(ResultSet rs, int row) throws SQLException {
        return new Hold(
                rs.getObject("id", UUID.class),
                rs.getObject("sale_id", UUID.class),
                rs.getString("customer_id"),
                rs.getInt("quantity"),
                HoldStatus.valueOf(rs.getString("status")),
                rs.getObject("expires_at", OffsetDateTime.class).toInstant());
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
