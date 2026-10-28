package dev.lucaswotta.tickethit.ticketing.hold;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class HoldRepository {

    private static final String COLUMNS =
            "id, sale_id, customer_id, quantity, status, expires_at, created_at, payment_requested_at, last_ticket_number";

    private final JdbcClient jdbc;

    HoldRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // Devolve o que o banco gravou: o timestamptz guarda microssegundos, e o
    // Instant do Java pode ter nanossegundos.
    Hold create(UUID saleId, String customerId, int quantity, UUID passId, Instant expiresAt) {
        return jdbc.sql("""
                        insert into hold (sale_id, customer_id, quantity, pass_id, status, expires_at)
                        values (:saleId, :customerId, :quantity, :passId, 'HELD', :expiresAt)
                        returning %s
                        """.formatted(COLUMNS))
                .param("saleId", saleId)
                .param("customerId", customerId)
                .param("quantity", quantity)
                .param("passId", passId)
                .param("expiresAt", utc(expiresAt))
                .query(HoldRepository::mapHold)
                .single();
    }

    Optional<Hold> find(UUID holdId) {
        return jdbc.sql("""
                        select %s
                          from hold
                         where id = :holdId
                        """.formatted(COLUMNS))
                .param("holdId", holdId)
                .query(HoldRepository::mapHold)
                .optional();
    }

    boolean passUsed(UUID saleId, UUID passId) {
        return jdbc.sql("select exists (select 1 from hold where sale_id = :saleId and pass_id = :passId)")
                .param("saleId", saleId)
                .param("passId", passId)
                .query(Boolean.class)
                .single();
    }

    Optional<Hold> startPayment(UUID holdId, Instant now) {
        return jdbc.sql("""
                        update hold
                           set status = 'PAYING', payment_requested_at = :now
                         where id = :holdId
                           and status = 'HELD'
                           and expires_at > :now
                        returning %s
                        """.formatted(COLUMNS))
                .param("holdId", holdId)
                .param("now", utc(now))
                .query(HoldRepository::mapHold)
                .optional();
    }

    void revertPayment(UUID holdId) {
        jdbc.sql("update hold set status = 'HELD', payment_requested_at = null where id = :holdId and status = 'PAYING'")
                .param("holdId", holdId)
                .update();
    }

    /**
     * Confirma o hold e numera a compra pelo total vendido. O UPDATE em sale
     * trava a linha da venda, então duas confirmações simultâneas recebem
     * números diferentes. Um evento repetido não acha o hold em HELD ou
     * PAYING e não muda nada.
     */
    Optional<Hold> confirm(UUID holdId, Instant now) {
        return jdbc.sql("""
                        with target as (
                            select id, sale_id, quantity
                              from hold
                             where id = :holdId
                               and status in ('HELD', 'PAYING')
                               for update
                        ),
                        sold as (
                            update sale
                               set sold_tickets = sold_tickets + target.quantity
                              from target
                             where sale.id = target.sale_id
                         returning sale.sold_tickets
                        )
                        update hold
                           set status = 'CONFIRMED',
                               confirmed_at = :now,
                               last_ticket_number = (select sold_tickets from sold)
                          from target
                         where hold.id = target.id
                        returning %s
                        """.formatted(prefixed("hold.")))
                .param("holdId", holdId)
                .param("now", utc(now))
                .query(HoldRepository::mapHold)
                .optional();
    }

    /** Libera o hold, devolve os ingressos e informa o estoque resultante; vazio se não havia o que liberar. */
    Optional<Released> release(UUID holdId) {
        return jdbc.sql("""
                        with released as (
                            update hold
                               set status = 'RELEASED'
                             where id = :holdId
                               and status in ('HELD', 'PAYING')
                         returning sale_id, customer_id, quantity
                        )
                        update sale
                           set available_tickets = available_tickets + released.quantity
                          from released
                         where sale.id = released.sale_id
                     returning sale.id, released.customer_id, sale.available_tickets
                        """)
                .param("holdId", holdId)
                .query((rs, row) -> new Released(
                        rs.getObject("id", UUID.class), rs.getString("customer_id"), rs.getInt("available_tickets")))
                .optional();
    }

    record Released(UUID saleId, String customerId, int availableTickets) {}

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
                instant(rs, "expires_at"),
                instant(rs, "created_at"),
                instant(rs, "payment_requested_at"),
                rs.getObject("last_ticket_number", Integer.class));
    }

    // No UPDATE ... FROM target, id, sale_id e quantity existem nas duas tabelas.
    private static String prefixed(String prefix) {
        return Arrays.stream(COLUMNS.split(", ")).map(column -> prefix + column).collect(Collectors.joining(", "));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
