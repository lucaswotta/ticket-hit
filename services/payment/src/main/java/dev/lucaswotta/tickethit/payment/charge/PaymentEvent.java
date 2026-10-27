package dev.lucaswotta.tickethit.payment.charge;

import java.time.Instant;
import java.util.UUID;

/** Contrato do tópico {@code payment.events}. Ver docs/eventos.md. */
record PaymentEvent(String type, UUID chargeId, UUID holdId, Instant occurredAt) {

    static final String TOPIC = "payment.events";

    static PaymentEvent decided(Charge charge) {
        String type = charge.status() == ChargeStatus.APPROVED ? "PAYMENT_APPROVED" : "PAYMENT_DECLINED";
        return new PaymentEvent(type, charge.id(), charge.holdId(), charge.decidedAt());
    }
}
