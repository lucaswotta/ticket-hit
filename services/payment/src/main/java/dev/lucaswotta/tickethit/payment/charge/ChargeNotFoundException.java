package dev.lucaswotta.tickethit.payment.charge;

import java.util.UUID;

public class ChargeNotFoundException extends RuntimeException {

    ChargeNotFoundException(UUID chargeId) {
        super("Cobrança %s não encontrada".formatted(chargeId));
    }
}
