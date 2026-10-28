package dev.lucaswotta.tickethit.simulator.room;

import dev.lucaswotta.tickethit.simulator.bot.Outcome;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Contadores de uma sala, escritos por centenas de bots ao mesmo tempo. */
public final class RoomStats {

    private final Map<Outcome, LongAdder> outcomes = new ConcurrentHashMap<>();
    private final Map<Integer, LongAdder> httpStatuses = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> errors = new ConcurrentHashMap<>();

    void outcome(Outcome outcome) {
        outcomes.computeIfAbsent(outcome, ignored -> new LongAdder()).increment();
    }

    void httpStatus(int status) {
        httpStatuses.computeIfAbsent(status, ignored -> new LongAdder()).increment();
    }

    void error(String reason) {
        errors.computeIfAbsent(reason, ignored -> new LongAdder()).increment();
    }

    public Map<String, Long> outcomes() {
        Map<String, Long> counts = new TreeMap<>();
        outcomes.forEach((outcome, count) -> counts.put(outcome.name(), count.sum()));
        return counts;
    }

    public Map<Integer, Long> httpStatuses() {
        Map<Integer, Long> counts = new TreeMap<>();
        httpStatuses.forEach((status, count) -> counts.put(status, count.sum()));
        return counts;
    }

    /** Motivo de cada {@code ERROR}: a chamada e o status, ou a exceção. */
    public Map<String, Long> errors() {
        Map<String, Long> counts = new TreeMap<>();
        errors.forEach((reason, count) -> counts.put(reason, count.sum()));
        return counts;
    }
}
