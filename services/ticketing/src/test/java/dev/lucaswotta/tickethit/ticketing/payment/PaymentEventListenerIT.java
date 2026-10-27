package dev.lucaswotta.tickethit.ticketing.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.lucaswotta.tickethit.ticketing.hold.Hold;
import dev.lucaswotta.tickethit.ticketing.hold.HoldService;
import dev.lucaswotta.tickethit.ticketing.hold.HoldStatus;
import dev.lucaswotta.tickethit.ticketing.sale.InventoryStrategy;
import dev.lucaswotta.tickethit.ticketing.sale.SaleRepository;
import dev.lucaswotta.tickethit.ticketing.support.IntegrationTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.kafka.core.KafkaTemplate;

@IntegrationTest
class PaymentEventListenerIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    KafkaConnectionDetails kafkaConnection;

    @Autowired
    HoldService holdService;

    @Autowired
    SaleRepository sales;

    @Test
    void approvedEventConfirmsHoldAndIgnoresUnknownFields() throws Exception {
        Hold hold = holdService.hold(sales.open("Noite 1", 10, InventoryStrategy.ATOMIC), "ana", 2, UUID.randomUUID());

        publish(hold.id(), """
                {"type": "PAYMENT_APPROVED", "chargeId": "%s", "holdId": "%s",
                 "occurredAt": "2026-10-27T21:00:00Z", "campoNovo": 1}
                """.formatted(UUID.randomUUID(), hold.id()));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            Hold confirmed = holdService.find(hold.id());
            assertThat(confirmed.status()).isEqualTo(HoldStatus.CONFIRMED);
            assertThat(confirmed.lastTicketNumber()).isEqualTo(2);
        });
    }

    @Test
    void declinedEventReleasesTickets() throws Exception {
        UUID saleId = sales.open("Noite 1", 10, InventoryStrategy.ATOMIC);
        Hold hold = holdService.hold(saleId, "ana", 2, UUID.randomUUID());

        publish(hold.id(), """
                {"type": "PAYMENT_DECLINED", "chargeId": "%s", "holdId": "%s", "occurredAt": "2026-10-27T21:00:00Z"}
                """.formatted(UUID.randomUUID(), hold.id()));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            assertThat(holdService.find(hold.id()).status()).isEqualTo(HoldStatus.RELEASED);
            assertThat(sales.availableTickets(saleId)).isEqualTo(10);
        });
    }

    @Test
    void malformedEventGoesToDeadLetterTopic() throws Exception {
        String key = UUID.randomUUID().toString();

        kafka.send("payment-events", key, "isto não é json").get();

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(deadLetterKeys()).contains(key));
    }

    private void publish(UUID holdId, String payload) throws Exception {
        kafka.send("payment-events", holdId.toString(), payload).get();
    }

    private List<String> deadLetterKeys() {
        Map<String, Object> config = Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConnection.getBootstrapServers());
        List<String> keys = new ArrayList<>();
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            List<TopicPartition> partitions = consumer.partitionsFor("payment-events-dlt").stream()
                    .map(info -> new TopicPartition(info.topic(), info.partition()))
                    .toList();
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            Map<TopicPartition, Long> end = consumer.endOffsets(partitions);
            while (partitions.stream().anyMatch(partition -> consumer.position(partition) < end.get(partition))) {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> keys.add(record.key()));
            }
        }
        return keys;
    }
}
