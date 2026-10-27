package dev.lucaswotta.tickethit.payment.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(value = """
            select *
              from outbox_event
             where published_at is null
             order by created_at
             limit :limit
               for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> lockUnpublished(int limit);
}
