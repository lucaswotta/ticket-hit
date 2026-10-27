package dev.lucaswotta.tickethit.ticketing.hold;

/**
 * HELD → PAYING → CONFIRMED, ou RELEASED se o pagamento for recusado.
 * HELD → EXPIRED quando o prazo vence. PAYING não expira: o payment sempre
 * chega a uma decisão, e o evento sai pelo outbox.
 */
public enum HoldStatus {
    HELD,
    PAYING,
    CONFIRMED,
    EXPIRED,
    RELEASED
}
