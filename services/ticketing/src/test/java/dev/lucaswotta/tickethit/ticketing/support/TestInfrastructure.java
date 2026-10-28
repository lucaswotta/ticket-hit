package dev.lucaswotta.tickethit.ticketing.support;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.config.TopicBuilder;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
class TestInfrastructure {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:18.6-alpine");
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka:4.3.2");
    }

    // Fora dos testes, os tópicos vêm de infra/kafka/create-topics.sh.
    @Bean
    NewTopic paymentEvents() {
        return TopicBuilder.name("payment-events").partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic telemetryEvents() {
        return TopicBuilder.name("telemetry-events").partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic paymentEventsDeadLetter() {
        return TopicBuilder.name("payment-events-dlt").partitions(3).replicas(1).build();
    }

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock();
    }
}
