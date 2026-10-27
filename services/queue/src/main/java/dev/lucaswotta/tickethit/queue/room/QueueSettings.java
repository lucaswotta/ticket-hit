package dev.lucaswotta.tickethit.queue.room;

import java.time.Instant;

record QueueSettings(Instant opensAt, int admissionsPerSecond) {}
