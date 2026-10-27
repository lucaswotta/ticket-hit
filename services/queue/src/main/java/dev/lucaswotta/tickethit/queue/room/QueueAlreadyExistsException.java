package dev.lucaswotta.tickethit.queue.room;

import java.util.UUID;

public class QueueAlreadyExistsException extends RuntimeException {

    QueueAlreadyExistsException(UUID saleId) {
        super("Fila da venda %s já existe".formatted(saleId));
    }
}
