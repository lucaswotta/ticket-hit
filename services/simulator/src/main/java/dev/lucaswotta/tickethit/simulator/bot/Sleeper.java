package dev.lucaswotta.tickethit.simulator.bot;

import java.time.Duration;

/** Espera do bot; nos testes, uma implementação que não espera. */
@FunctionalInterface
public interface Sleeper {

    void sleep(Duration duration) throws InterruptedException;
}
