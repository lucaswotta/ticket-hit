package dev.lucaswotta.tickethit.ticketing.payment;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ticketing.payment")
public record PaymentClientProperties(
        URI baseUrl, Duration connectTimeout, Duration readTimeout, int maxRetries, Duration retryDelay) {}
