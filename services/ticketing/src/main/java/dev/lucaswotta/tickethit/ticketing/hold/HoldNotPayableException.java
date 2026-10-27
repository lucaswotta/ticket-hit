package dev.lucaswotta.tickethit.ticketing.hold;

import java.util.UUID;

public class HoldNotPayableException extends RuntimeException {

    public HoldNotPayableException(UUID holdId, HoldStatus status) {
        super("Hold %s não pode ser pago no status %s".formatted(holdId, status));
    }
}
