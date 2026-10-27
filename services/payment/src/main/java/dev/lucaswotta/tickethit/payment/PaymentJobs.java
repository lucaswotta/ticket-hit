package dev.lucaswotta.tickethit.payment;

import dev.lucaswotta.tickethit.payment.charge.ChargeProcessor;
import dev.lucaswotta.tickethit.payment.outbox.OutboxRelay;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBooleanProperty(name = "payment.jobs-enabled", matchIfMissing = true)
class PaymentJobs {

    private final ChargeProcessor processor;
    private final OutboxRelay relay;

    PaymentJobs(ChargeProcessor processor, OutboxRelay relay) {
        this.processor = processor;
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${payment.processing-interval}")
    void decideDueCharges() {
        processor.processDue();
    }

    @Scheduled(fixedDelayString = "${payment.outbox-interval}")
    void publishOutbox() throws Exception {
        relay.publishPending();
    }
}
