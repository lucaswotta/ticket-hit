package dev.lucaswotta.tickethit.ticketing.sale;

import java.util.OptionalInt;
import java.util.UUID;

public interface TicketInventory {

    InventoryStrategy strategy();

    /** Retira os ingressos e devolve o estoque restante, ou vazio se não havia quantidade suficiente. */
    OptionalInt tryWithdraw(UUID saleId, int quantity);
}
