package dev.lucaswotta.tickethit.queue.support;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
class TestInfrastructure {

    @Bean
    @ServiceConnection
    RedisContainer redis() {
        return new RedisContainer("redis:8.10.3-alpine");
    }

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock();
    }
}
