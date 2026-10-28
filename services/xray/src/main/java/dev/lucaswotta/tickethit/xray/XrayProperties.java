package dev.lucaswotta.tickethit.xray;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("xray")
public record XrayProperties(
        int backlogSize, int maxStreamsPerSale, Duration streamTimeout, Duration idleFeedTtl, Duration heartbeatInterval) {}
