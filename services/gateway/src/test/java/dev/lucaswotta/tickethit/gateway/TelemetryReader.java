package dev.lucaswotta.tickethit.gateway;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Lê o tópico telemetry-events do início ao fim, sem consumer group, e filtra por venda. */
final class TelemetryReader {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private TelemetryReader() {}

    static List<JsonNode> eventsOf(List<String> bootstrapServers, UUID saleId) {
        Map<String, Object> config = Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        List<JsonNode> events = new ArrayList<>();
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            List<TopicPartition> partitions = consumer.partitionsFor("telemetry-events").stream()
                    .map(info -> new TopicPartition(info.topic(), info.partition()))
                    .toList();
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            Map<TopicPartition, Long> end = consumer.endOffsets(partitions);
            while (partitions.stream().anyMatch(partition -> consumer.position(partition) < end.get(partition))) {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (saleId.toString().equals(record.key())) {
                        events.add(JSON.readTree(record.value()));
                    }
                });
            }
        }
        return events;
    }
}
