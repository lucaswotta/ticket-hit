package dev.lucaswotta.tickethit.ticketing.pass;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ticketing.pass")
public record PassProperties(String publicKey, Duration clockSkew) {}
