package dev.lucaswotta.tickethit.ticketing.sale;

import java.util.UUID;

public interface TicketInventory {

    InventoryStrategy strategy();

    /** Retira os ingressos do estoque e informa se havia quantidade suficiente. */
    boolean tryWithdraw(UUID saleId, int quantity);
}
