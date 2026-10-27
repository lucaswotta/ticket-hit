package dev.lucaswotta.tickethit.payment.web;

import dev.lucaswotta.tickethit.payment.charge.Charge;
import dev.lucaswotta.tickethit.payment.charge.ChargeStatus;
import java.time.Instant;
import java.util.UUID;

record ChargeResponse(UUID id, UUID holdId, ChargeStatus status, Instant createdAt, Instant decidedAt) {

    static ChargeResponse of(Charge charge) {
        return new ChargeResponse(
                charge.id(), charge.holdId(), charge.status(), charge.createdAt(), charge.decidedAt());
    }
}
