package dev.lucaswotta.tickethit.xray;

import java.util.UUID;

class TooManyStreamsException extends RuntimeException {

    TooManyStreamsException(UUID saleId, int max) {
        super("A venda %s já tem %d raio-x abertos".formatted(saleId, max));
    }
}
