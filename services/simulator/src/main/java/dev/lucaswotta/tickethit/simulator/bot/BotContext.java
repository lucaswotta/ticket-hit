package dev.lucaswotta.tickethit.simulator.bot;

import dev.lucaswotta.tickethit.simulator.api.Reply;
import dev.lucaswotta.tickethit.simulator.api.TicketHitApi;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

/** O que um bot sabe da sala em que está. */
public record BotContext(
        TicketHitApi api,
        UUID saleId,
        String botId,
        Instant opensAt,
        Instant deadline,
        Duration reaction,
        LeakedPasses leakedPasses,
        Clock clock,
        Sleeper sleeper,
        Consumer<String> problems) {

    /** Registra uma resposta inesperada e devolve {@link Outcome#ERROR} para o bot terminar. */
    Outcome unexpected(String call, Reply reply) {
        problems.accept(call + " HTTP " + reply.status() + (reply.code() == null ? "" : " " + reply.code()));
        return Outcome.ERROR;
    }

    boolean beforeDeadline() {
        return clock.instant().isBefore(deadline);
    }

    void sleepUntil(Instant moment) throws InterruptedException {
        Duration remaining = Duration.between(clock.instant(), moment);
        if (!remaining.isNegative() && !remaining.isZero()) {
            sleeper.sleep(remaining);
        }
    }
}
