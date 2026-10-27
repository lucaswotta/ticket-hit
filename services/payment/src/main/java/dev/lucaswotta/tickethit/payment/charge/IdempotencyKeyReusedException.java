package dev.lucaswotta.tickethit.payment.charge;

public class IdempotencyKeyReusedException extends RuntimeException {

    IdempotencyKeyReusedException(String idempotencyKey) {
        super("Idempotency-Key %s já usada para outro hold".formatted(idempotencyKey));
    }
}
