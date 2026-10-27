package dev.lucaswotta.tickethit.queue.room;

import java.time.Instant;

/** Situação de um comprador na fila. Campos que não se aplicam ao status ficam nulos. */
public record Entry(
        EntryStatus status,
        Instant opensAt,
        Long position,
        Long waiting,
        Long estimatedWaitSeconds,
        String pass,
        Instant passExpiresAt) {}
