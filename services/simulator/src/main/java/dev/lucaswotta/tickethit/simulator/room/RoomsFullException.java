package dev.lucaswotta.tickethit.simulator.room;

import java.time.Duration;

public class RoomsFullException extends RuntimeException {

    private final Duration retryAfter;

    RoomsFullException(int maxRooms, Duration retryAfter) {
        super("A demo está em uso por outra pessoa (%d sala por vez)".formatted(maxRooms));
        this.retryAfter = retryAfter;
    }

    /** Tempo estimado até uma sala terminar. */
    public Duration retryAfter() {
        return retryAfter;
    }
}
