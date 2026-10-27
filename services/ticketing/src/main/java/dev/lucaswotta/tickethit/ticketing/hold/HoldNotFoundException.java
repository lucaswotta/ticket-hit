package dev.lucaswotta.tickethit.ticketing.hold;

import java.util.UUID;

public class HoldNotFoundException extends RuntimeException {

    public HoldNotFoundException(UUID holdId) {
        super("Hold %s não encontrado".formatted(holdId));
    }
}
