package dev.lucaswotta.tickethit.ticketing.telemetry;

import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.ProducerListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * Eventos para o raio-x, no tópico telemetry-events (contrato em docs/eventos.md).
 * Diferente dos eventos de negócio, não passam por outbox: perder um apaga um
 * ponto da animação, não um ingresso. Por isso publicar nunca pode atrasar a compra.
 */
@Component
public class Telemetry {

    public static final String TOPIC = "telemetry-events";

    private static final Logger log = LoggerFactory.getLogger(Telemetry.class);
    private static final long WARN_INTERVAL_MILLIS = Duration.ofSeconds(30).toMillis();

    private final KafkaTemplate<Object, Object> kafka;
    private final JsonMapper json;
    private final Clock clock;
    private final AtomicLong lastWarning = new AtomicLong();

    Telemetry(ProducerFactory<Object, Object> producers, JsonMapper json, Clock clock) {
        // Com o Kafka fora, o padrão do produtor bloqueia a thread por até 60 s
        // esperando metadados; aqui desiste em 200 ms e o evento se perde.
        this.kafka = new KafkaTemplate<>(producers, Map.of(
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 200,
                ProducerConfig.LINGER_MS_CONFIG, 5));
        // O aviso limitado de warn() substitui o log de erro por mensagem do Spring Kafka.
        this.kafka.setProducerListener(new ProducerListener<>() {});
        this.json = json;
        this.clock = clock;
    }

    public void publish(UUID saleId, String type, String customerId, Map<String, Object> data) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("saleId", saleId.toString());
        event.put("service", "ticketing");
        event.put("type", type);
        event.put("at", clock.instant().toString());
        event.put("customerId", customerId);
        event.put("data", data);
        try {
            kafka.send(TOPIC, saleId.toString(), json.writeValueAsString(event))
                    .whenComplete((sent, failure) -> {
                        if (failure != null) {
                            warn(failure);
                        }
                    });
        } catch (RuntimeException failure) {
            warn(failure);
        }
    }

    /** Dentro de uma transação, publica só depois do commit: o raio-x não mostra o que foi desfeito. */
    public void publishAfterCommit(UUID saleId, String type, String customerId, Map<String, Object> data) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish(saleId, type, customerId, data);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish(saleId, type, customerId, data);
            }
        });
    }

    // O primeiro envio busca os metadados do tópico, o que com a JVM recém-iniciada
    // passa dos 200 ms e perderia os primeiros eventos. Busca ao subir, fora das
    // requisições. A busca segue no cliente depois do timeout, então tentar de
    // novo costuma achar os metadados prontos.
    @EventListener(ApplicationReadyEvent.class)
    void loadTopicMetadata() {
        Thread.ofVirtual().start(() -> {
            RuntimeException last = null;
            for (int attempt = 0; attempt < 50; attempt++) {
                try {
                    kafka.partitionsFor(TOPIC);
                    return;
                } catch (RuntimeException notYet) {
                    last = notYet;
                    LockSupport.parkNanos(Duration.ofMillis(200).toNanos());
                }
            }
            warn(last);
        });
    }

    // Um aviso a cada 30 s no máximo: com o Kafka fora, cada compra falharia ao publicar.
    private void warn(Throwable failure) {
        long now = System.currentTimeMillis();
        long last = lastWarning.get();
        if (now - last >= WARN_INTERVAL_MILLIS && lastWarning.compareAndSet(last, now)) {
            log.warn("Evento de telemetria perdido: {}", failure.getMessage());
        }
    }

    @PreDestroy
    void close() {
        kafka.destroy();
    }
}
