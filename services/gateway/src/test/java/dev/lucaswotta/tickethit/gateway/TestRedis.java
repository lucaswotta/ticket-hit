package dev.lucaswotta.tickethit.gateway;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
class TestRedis {

    @Bean
    @ServiceConnection
    RedisContainer redis() {
        return new RedisContainer("redis:8.10.3-alpine");
    }
}
