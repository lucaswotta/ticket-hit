package dev.lucaswotta.tickethit.simulator.bot;

import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Passes de bots compradores que os bots abusivos tentam usar como se fossem seus. */
public final class LeakedPasses {

    private final ConcurrentLinkedQueue<String> passes = new ConcurrentLinkedQueue<>();

    void leak(String pass) {
        passes.add(pass);
    }

    Optional<String> any() {
        return Optional.ofNullable(passes.peek());
    }
}
