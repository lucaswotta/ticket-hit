package dev.lucaswotta.tickethit.ticketing.hold;

import java.time.Instant;
import java.util.UUID;

public record Hold(UUID id, UUID saleId, String customerId, int quantity, HoldStatus status, Instant expiresAt) {}
