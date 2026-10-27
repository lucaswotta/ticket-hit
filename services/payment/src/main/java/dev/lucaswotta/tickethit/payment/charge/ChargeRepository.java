package dev.lucaswotta.tickethit.payment.charge;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface ChargeRepository extends JpaRepository<Charge, UUID> {

    Optional<Charge> findByIdempotencyKey(String idempotencyKey);

    // Com a mesma chave em requisições simultâneas, a segunda espera a primeira
    // no índice unique e não insere nada. Devolve 1 se inseriu, 0 se já existia.
    @Modifying
    @Query(value = """
            insert into charge (idempotency_key, hold_id, customer_id, status, created_at)
            values (:idempotencyKey, :holdId, :customerId, 'PENDING', :createdAt)
            on conflict (idempotency_key) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(String idempotencyKey, UUID holdId, String customerId, Instant createdAt);

    // SQL nativo para o SKIP LOCKED ficar explícito: processadores em paralelo
    // pegam cobranças diferentes, e nenhuma é decidida duas vezes.
    @Query(value = """
            select *
              from charge
             where status = 'PENDING'
               and created_at <= :cutoff
             order by created_at
             limit :limit
               for update skip locked
            """, nativeQuery = true)
    List<Charge> lockPendingCreatedBefore(Instant cutoff, int limit);
}
