package dev.lucaswotta.tickethit.payment.outbox;

import dev.lucaswotta.tickethit.payment.PaymentProperties;
import java.time.Clock;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publica no Kafka os eventos ainda não publicados. A entrega é pelo menos
 * uma vez: se o Kafka confirmar e a transação falhar antes do commit, o evento
 * sai de novo no próximo ciclo. Quem consome precisa tolerar repetição.
 */
@Component
public class OutboxRelay {

    private final OutboxEventRepository events;
    private final KafkaTemplate<String, String> kafka;
    private final PaymentProperties properties;
    private final Clock clock;

    OutboxRelay(
            OutboxEventRepository events,
            KafkaTemplate<String, String> kafka,
            PaymentProperties properties,
            Clock clock) {
        this.events = events;
        this.kafka = kafka;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public int publishPending() throws ExecutionException, InterruptedException, TimeoutException {
        var pending = events.lockUnpublished(properties.batchSize());
        for (OutboxEvent event : pending) {
            kafka.send(event.topic(), event.messageKey(), event.payload()).get(10, TimeUnit.SECONDS);
            event.markPublished(clock.instant());
        }
        return pending.size();
    }
}
