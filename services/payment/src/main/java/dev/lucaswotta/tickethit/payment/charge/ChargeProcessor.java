package dev.lucaswotta.tickethit.payment.charge;

import dev.lucaswotta.tickethit.payment.PaymentProperties;
import dev.lucaswotta.tickethit.payment.outbox.Outbox;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChargeProcessor {

    private final ChargeRepository charges;
    private final FakeGateway gateway;
    private final Outbox outbox;
    private final PaymentProperties properties;
    private final Clock clock;

    ChargeProcessor(
            ChargeRepository charges, FakeGateway gateway, Outbox outbox, PaymentProperties properties, Clock clock) {
        this.charges = charges;
        this.gateway = gateway;
        this.outbox = outbox;
        this.properties = properties;
        this.clock = clock;
    }

    // A decisão e o evento que a anuncia são gravados na mesma transação:
    // não existe cobrança decidida sem evento, nem evento sem cobrança.
    @Transactional
    public int processDue() {
        Instant now = clock.instant();
        var due = charges.lockPendingCreatedBefore(now.minus(gateway.decisionDelay()), properties.batchSize());
        for (Charge charge : due) {
            charge.decide(gateway.approves(), now);
            // Chave = hold: eventos do mesmo hold caem na mesma partição e
            // chegam na ordem em que foram publicados.
            outbox.append(PaymentEvent.TOPIC, charge.holdId().toString(), PaymentEvent.decided(charge));
        }
        return due.size();
    }
}
