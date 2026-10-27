package dev.lucaswotta.tickethit.payment.charge;

/** A cobrança e se ela já existia para a mesma Idempotency-Key. */
public record ChargeResult(Charge charge, boolean replayed) {}
