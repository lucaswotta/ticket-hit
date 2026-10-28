package dev.lucaswotta.tickethit.queue.support;

import com.redis.testcontainers.RedisContainer;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.config.TopicBuilder;
import org.testcontainers.kafka.KafkaContainer;

@TestConfiguration(proxyBeanMethods = false)
class TestInfrastructure {

    @Bean
    @ServiceConnection
    RedisContainer redis() {
        return new RedisContainer("redis:8.10.3-alpine");
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka:4.3.2");
    }

    // Fora dos testes, os tópicos vêm de infra/kafka/create-topics.sh.
    @Bean
    NewTopic telemetryEvents() {
        return TopicBuilder.name("telemetry-events").partitions(3).replicas(1).build();
    }

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock();
    }
}
