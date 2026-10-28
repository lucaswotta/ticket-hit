package dev.lucaswotta.tickethit.simulator.room;

public class TooManyBotsException extends RuntimeException {

    TooManyBotsException(int requested, int max) {
        super("%d bots pedidos; o limite por sala é %d".formatted(requested, max));
    }
}
