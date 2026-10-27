package dev.lucaswotta.tickethit.ticketing.payment;

import dev.lucaswotta.tickethit.ticketing.hold.HoldService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final HoldService holdService;
    private final JsonMapper json;

    PaymentEventListener(HoldService holdService, JsonMapper json) {
        this.holdService = holdService;
        this.json = json;
    }

    // A entrega é pelo menos uma vez; confirm e release não fazem nada quando o
    // hold já saiu de HELD ou PAYING, então um evento repetido é inofensivo.
    @KafkaListener(topics = "payment-events")
    void onPaymentEvent(String payload) {
        PaymentEvent event = json.readValue(payload, PaymentEvent.class);
        switch (event.type()) {
            case "PAYMENT_APPROVED" -> holdService.confirm(event.holdId());
            case "PAYMENT_DECLINED" -> holdService.release(event.holdId());
            default -> log.warn("Evento de pagamento com tipo desconhecido ignorado: {}", event.type());
        }
    }
}
