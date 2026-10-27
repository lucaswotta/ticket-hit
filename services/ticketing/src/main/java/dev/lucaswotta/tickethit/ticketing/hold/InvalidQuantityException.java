package dev.lucaswotta.tickethit.ticketing.hold;

public class InvalidQuantityException extends RuntimeException {

    public InvalidQuantityException(int quantity, int maxQuantity) {
        super("Quantidade %d fora do limite de 1 a %d por hold".formatted(quantity, maxQuantity));
    }
}
