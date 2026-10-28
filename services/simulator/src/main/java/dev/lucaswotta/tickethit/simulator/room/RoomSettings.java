package dev.lucaswotta.tickethit.simulator.room;

import java.time.Duration;

public record RoomSettings(String inventoryStrategy, int tickets, int bots, int admissionsPerSecond, Duration lobby) {}
