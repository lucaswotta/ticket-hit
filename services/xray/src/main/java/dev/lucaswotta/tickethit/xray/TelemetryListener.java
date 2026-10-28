package dev.lucaswotta.tickethit.xray;

import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class TelemetryListener {

    private final XrayHub hub;

    TelemetryListener(XrayHub hub) {
        this.hub = hub;
    }

    // Um consumer group por instância: cada xray precisa de todos os eventos,
    // porque os navegadores de uma sala podem estar ligados a qualquer instância.
    // O JSON segue como chegou; a chave da mensagem é a venda.
    @KafkaListener(topics = "telemetry-events", groupId = "xray-#{T(java.util.UUID).randomUUID()}")
    void onTelemetry(ConsumerRecord<String, String> record) {
        hub.accept(UUID.fromString(record.key()), record.value());
    }
}
