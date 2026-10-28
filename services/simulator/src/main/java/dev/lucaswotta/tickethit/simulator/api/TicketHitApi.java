package dev.lucaswotta.tickethit.simulator.api;

import java.time.Instant;
import java.util.UUID;

/**
 * As chamadas que o simulator e os bots fazem pelo gateway. A interface existe
 * para os testes trocarem o sistema inteiro por um falso em memória.
 */
public interface TicketHitApi {

    UUID openSale(String name, int tickets, String inventoryStrategy);

    void openQueue(UUID saleId, Instant opensAt, int admissionsPerSecond);

    Reply sale(UUID saleId);

    Reply join(UUID saleId, String bot);

    Reply entry(UUID saleId, String bot);

    /** {@code pass} nulo manda o pedido sem o header Queue-Pass. */
    Reply hold(UUID saleId, String bot, String pass, int quantity);

    Reply pay(UUID holdId, String bot);

    Reply holdStatus(UUID holdId, String bot);
}
