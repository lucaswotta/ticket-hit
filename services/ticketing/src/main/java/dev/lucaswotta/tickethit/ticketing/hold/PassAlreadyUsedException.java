package dev.lucaswotta.tickethit.ticketing.hold;

import java.util.UUID;

public class PassAlreadyUsedException extends RuntimeException {

    PassAlreadyUsedException(UUID saleId) {
        super("Passe já usado para um hold na venda %s".formatted(saleId));
    }
}
