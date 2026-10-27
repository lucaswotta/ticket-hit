package dev.lucaswotta.tickethit.queue.room;

import java.time.Instant;
import java.util.UUID;

public record QueueStats(UUID saleId, Instant opensAt, int admissionsPerSecond, long waiting, long admitted) {}
