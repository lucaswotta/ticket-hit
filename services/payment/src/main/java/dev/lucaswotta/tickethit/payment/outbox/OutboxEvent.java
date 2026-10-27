package dev.lucaswotta.tickethit.payment.outbox;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
class OutboxEvent {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private String topic;
    private String messageKey;

    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    private Instant createdAt;
    private Instant publishedAt;

    protected OutboxEvent() {}

    OutboxEvent(String topic, String messageKey, String payload, Instant createdAt) {
        this.topic = topic;
        this.messageKey = messageKey;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    void markPublished(Instant now) {
        publishedAt = now;
    }

    String topic() {
        return topic;
    }

    String messageKey() {
        return messageKey;
    }

    String payload() {
        return payload;
    }
}
