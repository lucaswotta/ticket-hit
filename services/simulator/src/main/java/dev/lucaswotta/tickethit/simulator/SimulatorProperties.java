package dev.lucaswotta.tickethit.simulator;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("simulator")
public record SimulatorProperties(
        URI gatewayUrl,
        int maxRooms,
        int maxBots,
        double abusiveShare,
        Duration roomTimeout,
        Defaults defaults,
        Reaction reaction) {

    public record Defaults(int tickets, int bots, int admissionsPerSecond, Duration lobby) {}

    public record Reaction(Duration min, Duration max) {}
}
