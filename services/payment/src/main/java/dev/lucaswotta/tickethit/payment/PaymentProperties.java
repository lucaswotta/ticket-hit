package dev.lucaswotta.tickethit.payment;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("payment")
public record PaymentProperties(int batchSize, Fake fake) {

    public record Fake(Duration decisionDelay, double declineRate, double unavailableRate) {}
}
