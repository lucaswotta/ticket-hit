package dev.lucaswotta.tickethit.xray;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"xray.max-streams-per-sale=2", "spring.kafka.consumer.auto-offset-reset=earliest"})
@Import(XrayIT.Infrastructure.class)
class XrayIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @LocalServerPort
    int port;

    @Autowired
    KafkaTemplate<String, String> kafka;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void streamSendsTheBacklogThenLiveEventsOfThatSaleOnly() throws Exception {
        UUID sale = UUID.randomUUID();
        UUID otherSale = UUID.randomUUID();
        publish(sale, "{\"type\":\"QUEUE_JOINED\"}");
        publish(otherSale, "{\"type\":\"OUTRA_SALA\"}");
        publish(sale, "{\"type\":\"QUEUE_ADMITTED\"}");
        // O xray só guarda o que já consumiu: espera os eventos chegarem antes de conectar.
        await().atMost(TIMEOUT).until(() -> hub.backlogOf(sale) == 2);

        List<String> received = open(sale);
        publish(sale, "{\"type\":\"HOLD_CREATED\"}");

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(received)
                .containsExactly(
                        "data:{\"type\":\"QUEUE_JOINED\"}",
                        "data:{\"type\":\"QUEUE_ADMITTED\"}",
                        "data:{\"type\":\"HOLD_CREATED\"}"));
    }

    @Test
    void streamsPerSaleAreLimited() throws Exception {
        UUID sale = UUID.randomUUID();
        open(sale);
        open(sale);

        HttpResponse<Void> third = http.send(request(sale), HttpResponse.BodyHandlers.discarding());

        assertThat(third.statusCode()).isEqualTo(429);
    }

    @Autowired
    XrayHub hub;

    private void publish(UUID sale, String json) throws Exception {
        kafka.send("telemetry-events", sale.toString(), json).get();
    }

    // Lê o stream numa thread virtual e guarda só as linhas de dados.
    private List<String> open(UUID sale) throws Exception {
        List<String> lines = new CopyOnWriteArrayList<>();
        HttpResponse<InputStream> response = http.send(request(sale), HttpResponse.BodyHandlers.ofInputStream());
        assertThat(response.statusCode()).isEqualTo(200);
        Thread.ofVirtual().start(() -> {
            try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        lines.add(line);
                    }
                }
            } catch (Exception closed) {
                // o teste terminou e a conexão foi encerrada
            }
        });
        return lines;
    }

    private HttpRequest request(UUID sale) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/xray/" + sale + "/events"))
                .header("Accept", "text/event-stream")
                .build();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Infrastructure {

        @Bean
        @ServiceConnection
        KafkaContainer kafka() {
            return new KafkaContainer("apache/kafka:4.3.2");
        }

        @Bean
        NewTopic telemetryEvents() {
            return TopicBuilder.name("telemetry-events").partitions(3).replicas(1).build();
        }
    }
}
