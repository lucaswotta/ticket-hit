package dev.lucaswotta.tickethit.queue;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("queue")
public record QueueProperties(Duration roomTtl, Pass pass) {

    public record Pass(Duration ttl, String privateKey, String publicKey) {}
}
