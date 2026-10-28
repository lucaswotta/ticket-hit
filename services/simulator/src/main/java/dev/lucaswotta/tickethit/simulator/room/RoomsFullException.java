package dev.lucaswotta.tickethit.simulator.room;

public class RoomsFullException extends RuntimeException {

    RoomsFullException(int maxRooms) {
        super("As %d salas da demo estão ocupadas".formatted(maxRooms));
    }
}
