package dev.lucaswotta.tickethit.simulator.room;

import java.time.Instant;
import java.util.UUID;

public final class Room {

    public enum Status {
        RUNNING,
        FINISHED
    }

    private final UUID id;
    private final String owner;
    private final UUID saleId;
    private final RoomSettings settings;
    private final Instant opensAt;
    private final RoomStats stats = new RoomStats();
    private volatile Status status = Status.RUNNING;
    private volatile Instant finishedAt;

    Room(UUID id, String owner, UUID saleId, RoomSettings settings, Instant opensAt) {
        this.id = id;
        this.owner = owner;
        this.saleId = saleId;
        this.settings = settings;
        this.opensAt = opensAt;
    }

    void finish(Instant now) {
        finishedAt = now;
        status = Status.FINISHED;
    }

    public UUID id() {
        return id;
    }

    public String owner() {
        return owner;
    }

    public UUID saleId() {
        return saleId;
    }

    public RoomSettings settings() {
        return settings;
    }

    public Instant opensAt() {
        return opensAt;
    }

    public RoomStats stats() {
        return stats;
    }

    public Status status() {
        return status;
    }

    public Instant finishedAt() {
        return finishedAt;
    }
}
