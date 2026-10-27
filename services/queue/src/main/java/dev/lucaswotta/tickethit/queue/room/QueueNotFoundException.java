package dev.lucaswotta.tickethit.queue.room;

import java.util.UUID;

public class QueueNotFoundException extends RuntimeException {

    QueueNotFoundException(UUID saleId) {
        super("Fila da venda %s não encontrada".formatted(saleId));
    }
}
