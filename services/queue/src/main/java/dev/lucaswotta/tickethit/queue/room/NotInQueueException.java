package dev.lucaswotta.tickethit.queue.room;

import java.util.UUID;

public class NotInQueueException extends RuntimeException {

    NotInQueueException(UUID saleId, String customerId) {
        super("%s não está na fila da venda %s".formatted(customerId, saleId));
    }
}
