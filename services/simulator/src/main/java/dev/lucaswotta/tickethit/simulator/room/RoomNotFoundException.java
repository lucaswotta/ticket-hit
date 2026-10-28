package dev.lucaswotta.tickethit.simulator.room;

import java.util.UUID;

public class RoomNotFoundException extends RuntimeException {

    RoomNotFoundException(UUID roomId) {
        super("Sala %s não encontrada".formatted(roomId));
    }
}
