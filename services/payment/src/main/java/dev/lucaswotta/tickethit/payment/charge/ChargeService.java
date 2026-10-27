package dev.lucaswotta.tickethit.payment.charge;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChargeService {

    private final ChargeRepository charges;
    private final Clock clock;

    ChargeService(ChargeRepository charges, Clock clock) {
        this.charges = charges;
        this.clock = clock;
    }

    /** Cria a cobrança ou devolve a que já existe para a mesma chave. */
    @Transactional
    public ChargeResult charge(String idempotencyKey, UUID holdId, String customerId) {
        boolean created = charges.insertIfAbsent(idempotencyKey, holdId, customerId, clock.instant()) == 1;
        Charge charge = charges.findByIdempotencyKey(idempotencyKey).orElseThrow();
        if (!charge.holdId().equals(holdId)) {
            throw new IdempotencyKeyReusedException(idempotencyKey);
        }
        return new ChargeResult(charge, !created);
    }

    public Charge find(UUID chargeId) {
        return charges.findById(chargeId).orElseThrow(() -> new ChargeNotFoundException(chargeId));
    }
}
