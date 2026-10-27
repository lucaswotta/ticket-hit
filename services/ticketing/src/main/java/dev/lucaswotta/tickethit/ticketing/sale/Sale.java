package dev.lucaswotta.tickethit.ticketing.sale;

import java.util.UUID;

public record Sale(UUID id, String name, int totalTickets, int availableTickets, InventoryStrategy inventoryStrategy) {}
