package dev.lucaswotta.tickethit.queue.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.UUID;

record CreateQueueRequest(
        @NotNull UUID saleId, @NotNull Instant opensAt, @NotNull @Positive @Max(1_000) Integer admissionsPerSecond) {}
