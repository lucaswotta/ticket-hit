package dev.lucaswotta.tickethit.ticketing.payment;

import java.time.Instant;
import java.util.UUID;

/** Contrato de {@code payment-events}, lado do consumidor. Ver docs/eventos.md. */
record PaymentEvent(String type, UUID chargeId, UUID holdId, Instant occurredAt) {}
