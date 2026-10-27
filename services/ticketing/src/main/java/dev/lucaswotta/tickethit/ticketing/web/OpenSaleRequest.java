package dev.lucaswotta.tickethit.ticketing.web;

import dev.lucaswotta.tickethit.ticketing.sale.InventoryStrategy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

record OpenSaleRequest(@NotBlank String name, @NotNull @Positive Integer totalTickets, InventoryStrategy inventoryStrategy) {

    InventoryStrategy inventoryStrategyOrDefault() {
        return inventoryStrategy == null ? InventoryStrategy.ATOMIC : inventoryStrategy;
    }
}
