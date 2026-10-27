package dev.lucaswotta.tickethit.payment.outbox;

import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
public class Outbox {

    private final OutboxEventRepository events;
    private final JsonMapper json;
    private final Clock clock;

    Outbox(OutboxEventRepository events, JsonMapper json, Clock clock) {
        this.events = events;
        this.json = json;
        this.clock = clock;
    }

    // MANDATORY: gravar o evento fora da transação da mudança quebraria a
    // garantia do outbox, então chamar sem transação é erro de programação.
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String topic, String messageKey, Object event) {
        events.save(new OutboxEvent(topic, messageKey, json.writeValueAsString(event), clock.instant()));
    }
}
