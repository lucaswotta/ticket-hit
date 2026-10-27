package dev.lucaswotta.tickethit.ticketing.hold;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ticketing.hold")
public record HoldProperties(Duration duration, int maxQuantity, int expirationBatchSize) {}
