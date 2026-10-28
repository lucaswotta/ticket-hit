package dev.lucaswotta.tickethit.simulator.web;

import dev.lucaswotta.tickethit.simulator.room.Room;
import dev.lucaswotta.tickethit.simulator.room.RoomSettings;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

record RoomResponse(
        UUID id,
        UUID saleId,
        Room.Status status,
        Instant opensAt,
        Instant finishedAt,
        RoomSettings settings,
        Map<String, Long> outcomes,
        Map<Integer, Long> httpStatuses,
        Map<String, Long> errors) {

    static RoomResponse of(Room room) {
        return new RoomResponse(
                room.id(),
                room.saleId(),
                room.status(),
                room.opensAt(),
                room.finishedAt(),
                room.settings(),
                room.stats().outcomes(),
                room.stats().httpStatuses(),
                room.stats().errors());
    }
}
