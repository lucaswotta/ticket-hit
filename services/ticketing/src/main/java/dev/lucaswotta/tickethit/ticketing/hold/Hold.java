package dev.lucaswotta.tickethit.ticketing.hold;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code lastTicketNumber} é a posição do último ingresso desta compra entre os
 * vendidos da venda ("você levou o ingresso 442 de 500").
 */
public record Hold(
        UUID id,
        UUID saleId,
        String customerId,
        int quantity,
        HoldStatus status,
        Instant expiresAt,
        Instant createdAt,
        Instant paymentRequestedAt,
        Integer lastTicketNumber) {}
