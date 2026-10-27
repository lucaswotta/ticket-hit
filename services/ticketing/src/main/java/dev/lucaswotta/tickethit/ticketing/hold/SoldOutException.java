package dev.lucaswotta.tickethit.ticketing.hold;

import java.util.UUID;

public class SoldOutException extends RuntimeException {

    public SoldOutException(UUID saleId, int quantity) {
        super("Venda %s sem %d ingresso(s) disponível(is)".formatted(saleId, quantity));
    }
}
