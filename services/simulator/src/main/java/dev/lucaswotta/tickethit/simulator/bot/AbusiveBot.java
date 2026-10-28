package dev.lucaswotta.tickethit.simulator.bot;

import dev.lucaswotta.tickethit.simulator.api.Reply;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Bot que ataca em vez de comprar. Cada ataque tem uma resposta esperada do
 * sistema; se ela não vier, o resultado é {@link Outcome#ATTACK_SUCCEEDED}.
 */
public final class AbusiveBot {

    private static final int FLOOD_REQUESTS = 30;

    private AbusiveBot() {}

    public static Outcome run(BotContext bot, Attack attack) throws InterruptedException {
        bot.sleepUntil(bot.opensAt());
        return switch (attack) {
            case FORGED_PASS -> expect(bot, bot.api().hold(bot.saleId(), bot.botId(), forgedPass(bot), 1), 403,
                    Outcome.BLOCKED_FORGED_PASS);
            case STOLEN_PASS -> stolenPass(bot);
            case NO_PASS -> expect(bot, bot.api().hold(bot.saleId(), bot.botId(), null, 1), 400, Outcome.BLOCKED_NO_PASS);
            case FLOOD -> flood(bot);
        };
    }

    private static Outcome stolenPass(BotContext bot) throws InterruptedException {
        while (bot.beforeDeadline()) {
            var pass = bot.leakedPasses().any();
            if (pass.isPresent()) {
                return expect(bot, bot.api().hold(bot.saleId(), bot.botId(), pass.get(), 1), 403,
                        Outcome.BLOCKED_STOLEN_PASS);
            }
            bot.sleeper().sleep(Duration.ofMillis(500));
        }
        return Outcome.GAVE_UP;
    }

    // Em paralelo: uma por vez, sob carga, as consultas se espaçam o bastante
    // para caber na reposição do balde e deixam de ser enxurrada.
    private static Outcome flood(BotContext bot) throws InterruptedException {
        List<Future<Reply>> requests = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < FLOOD_REQUESTS; i++) {
                requests.add(executor.submit(() -> bot.api().entry(bot.saleId(), bot.botId())));
            }
            boolean limited = false;
            for (Future<Reply> request : requests) {
                try {
                    limited |= request.get().is(429);
                } catch (ExecutionException failed) {
                    throw new IllegalStateException(failed.getCause());
                }
            }
            return limited ? Outcome.BLOCKED_FLOOD : Outcome.ATTACK_SUCCEEDED;
        }
    }

    private static Outcome expect(BotContext bot, Reply reply, int blockedStatus, Outcome blocked) {
        if (reply.is(blockedStatus)) {
            return blocked;
        }
        return reply.is(201) ? Outcome.ATTACK_SUCCEEDED : bot.unexpected("ataque", reply);
    }

    // Header e claims plausíveis, assinatura aleatória: só a chave privada da
    // queue produziria uma assinatura válida.
    private static String forgedPass(BotContext bot) {
        String header = "{\"alg\":\"ES256\",\"typ\":\"JWT\"}";
        String claims = """
                {"iss":"ticket-hit-queue","aud":["ticketing"],"sub":"%s","sid":"%s","jti":"%s","exp":%d}
                """.formatted(
                        bot.botId(),
                        bot.saleId(),
                        UUID.randomUUID(),
                        bot.clock().instant().plusSeconds(120).getEpochSecond())
                .strip();
        byte[] signature = new byte[64];
        ThreadLocalRandom.current().nextBytes(signature);
        var encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(header.getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString(claims.getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString(signature);
    }
}
