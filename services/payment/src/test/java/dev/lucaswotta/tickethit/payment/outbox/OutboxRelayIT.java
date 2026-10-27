package dev.lucaswotta.tickethit.payment.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import dev.lucaswotta.tickethit.payment.support.IntegrationTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class OutboxRelayIT {

    private static final String TOPIC = "payment-events";

    @Autowired
    Outbox outbox;

    @Autowired
    OutboxRelay relay;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    KafkaConnectionDetails kafka;

    @Autowired
    JdbcClient jdbc;

    @Test
    void publishesEachEventOnceAndMarksItPublished() throws Exception {
        String key = UUID.randomUUID().toString();
        transactions.executeWithoutResult(status -> outbox.append(TOPIC, key, Map.of("type", "TEST")));

        relay.publishPending();
        relay.publishPending();

        // O jsonb normaliza a formatação do payload; compara-se o conteúdo.
        assertThat(messagesWithKey(key)).singleElement().satisfies(message ->
                assertThat(JsonPath.<String>read(message.value(), "$.type")).isEqualTo("TEST"));
        assertThat(unpublishedWithKey(key)).isZero();
    }

    // Lê todas as partições do início ao fim, sem consumer group, para que uma
    // publicação repetida apareça na contagem.
    private List<ConsumerRecord<String, String>> messagesWithKey(String key) {
        Map<String, Object> config = Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            List<TopicPartition> partitions = consumer.partitionsFor(TOPIC).stream()
                    .map(info -> new TopicPartition(TOPIC, info.partition()))
                    .toList();
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            Map<TopicPartition, Long> end = consumer.endOffsets(partitions);
            while (partitions.stream().anyMatch(partition -> consumer.position(partition) < end.get(partition))) {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (key.equals(record.key())) {
                        found.add(record);
                    }
                });
            }
        }
        return found;
    }

    private int unpublishedWithKey(String key) {
        return jdbc.sql("select count(*) from outbox_event where message_key = :key and published_at is null")
                .param("key", key)
                .query(Integer.class)
                .single();
    }
}
