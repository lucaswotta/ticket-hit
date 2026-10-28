package dev.lucaswotta.tickethit.simulator.bot;

import dev.lucaswotta.tickethit.simulator.api.Reply;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/** Comprador comum: entra na fila, espera o passe, segura 1 ingresso e paga. */
public final class BuyerBot {

    private static final Duration MIN_POLL = Duration.ofMillis(500);
    private static final Duration MAX_POLL = Duration.ofSeconds(5);
    private static final int PAYMENT_ATTEMPTS = 3;
    private static final Reply ACCEPTED = new Reply(202, null);

    private BuyerBot() {}

    public static Outcome run(BotContext bot) throws InterruptedException {
        Reply joined = patiently(bot, () -> bot.api().join(bot.saleId(), bot.botId()));
        if (!joined.is(201) && !joined.is(200)) {
            return bot.unexpected("join", joined);
        }
        Waited waited = awaitPass(bot);
        if (waited.pass() == null) {
            return waited.outcome();
        }
        bot.leakedPasses().leak(waited.pass());
        bot.sleeper().sleep(bot.reaction());

        Reply held = patiently(bot, () -> bot.api().hold(bot.saleId(), bot.botId(), waited.pass(), 1));
        if (held.is(409) && "SOLD_OUT".equals(held.code())) {
            return Outcome.SOLD_OUT;
        }
        if (!held.is(201)) {
            return bot.unexpected("hold", held);
        }
        UUID holdId = UUID.fromString(held.text("id"));
        Reply paid = pay(bot, holdId);
        if (!paid.is(202)) {
            return bot.unexpected("pay", paid);
        }
        return awaitDecision(bot, holdId);
    }

    private static Waited awaitPass(BotContext bot) throws InterruptedException {
        while (bot.beforeDeadline()) {
            Reply entry = bot.api().entry(bot.saleId(), bot.botId());
            if (entry.is(429)) {
                bot.sleeper().sleep(Duration.ofSeconds(1));
                continue;
            }
            if (!entry.is(200)) {
                return new Waited(bot.unexpected("entry", entry), null);
            }
            switch (entry.text("status")) {
                case "ADMITTED" -> {
                    return new Waited(null, entry.text("pass"));
                }
                case "PASS_EXPIRED" -> {
                    return new Waited(Outcome.PASS_EXPIRED, null);
                }
                case "WAITING_DRAW" -> bot.sleepUntil(bot.opensAt().plusMillis(jitterMillis(1_000)));
                case "WAITING" -> bot.sleeper().sleep(pollInterval(entry.number("estimatedWaitSeconds")));
                default -> {
                    return new Waited(bot.unexpected("entry " + entry.text("status"), entry), null);
                }
            }
        }
        return new Waited(Outcome.GAVE_UP, null);
    }

    // Metade do tempo estimado, entre 0,5 s e 5 s: perto da vez, o bot consulta
    // mais; longe dela, não gasta requisição à toa.
    private static Duration pollInterval(long estimatedWaitSeconds) {
        Duration half = Duration.ofMillis(estimatedWaitSeconds * 500);
        if (half.compareTo(MIN_POLL) < 0) {
            return MIN_POLL;
        }
        return half.compareTo(MAX_POLL) > 0 ? MAX_POLL : half;
    }

    // 503 é o payment fora: tenta de novo com espera crescente. 504 é o gateway
    // desistindo de esperar; a cobrança pode ter seguido, então o bot confere o
    // hold antes de pagar de novo.
    private static Reply pay(BotContext bot, UUID holdId) throws InterruptedException {
        Reply paid = patiently(bot, () -> bot.api().pay(holdId, bot.botId()));
        for (int attempt = 1; (paid.is(503) || paid.is(504)) && attempt < PAYMENT_ATTEMPTS; attempt++) {
            if (paid.is(504) && !"HELD".equals(bot.api().holdStatus(holdId, bot.botId()).text("status"))) {
                return ACCEPTED;
            }
            bot.sleeper().sleep(Duration.ofSeconds(attempt));
            paid = patiently(bot, () -> bot.api().pay(holdId, bot.botId()));
        }
        return paid;
    }

    private static Outcome awaitDecision(BotContext bot, UUID holdId) throws InterruptedException {
        while (bot.beforeDeadline()) {
            Reply hold = bot.api().holdStatus(holdId, bot.botId());
            if (hold.is(200)) {
                switch (hold.text("status")) {
                    case "CONFIRMED" -> {
                        return Outcome.CONFIRMED;
                    }
                    case "RELEASED" -> {
                        return Outcome.DECLINED;
                    }
                    default -> {}
                }
            }
            bot.sleeper().sleep(Duration.ofSeconds(1));
        }
        return Outcome.GAVE_UP;
    }

    // 429 é o rate limit pedindo calma: espera 1 s e tenta de novo, como um
    // comprador real faria, até o prazo da sala.
    private static Reply patiently(BotContext bot, Supplier<Reply> call) throws InterruptedException {
        Reply reply = call.get();
        while (reply.is(429) && bot.beforeDeadline()) {
            bot.sleeper().sleep(Duration.ofSeconds(1));
            reply = call.get();
        }
        return reply;
    }

    private static long jitterMillis(int bound) {
        return ThreadLocalRandom.current().nextInt(bound);
    }

    private record Waited(Outcome outcome, String pass) {}
}
