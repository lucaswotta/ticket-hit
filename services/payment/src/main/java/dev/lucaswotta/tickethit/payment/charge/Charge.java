package dev.lucaswotta.tickethit.payment.charge;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
public class Charge {

    // Criada só por ChargeRepository.insertIfAbsent; o id vem do uuidv7() do banco.
    @Id
    private UUID id;

    private String idempotencyKey;
    private UUID holdId;
    private String customerId;

    @Enumerated(EnumType.STRING)
    private ChargeStatus status;

    private Instant createdAt;
    private Instant decidedAt;

    protected Charge() {}

    void decide(boolean approved, Instant now) {
        if (status != ChargeStatus.PENDING) {
            throw new IllegalStateException("Cobrança %s já decidida: %s".formatted(id, status));
        }
        status = approved ? ChargeStatus.APPROVED : ChargeStatus.DECLINED;
        // Na precisão do timestamptz: o evento gerado a partir desta entidade
        // carrega o mesmo instante que fica gravado no banco.
        decidedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public UUID id() {
        return id;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public UUID holdId() {
        return holdId;
    }

    public String customerId() {
        return customerId;
    }

    public ChargeStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant decidedAt() {
        return decidedAt;
    }
}
