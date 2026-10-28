package dev.lucaswotta.tickethit.simulator.bot;

import static org.assertj.core.api.Assertions.assertThat;

import dev.lucaswotta.tickethit.simulator.FakeTicketHit;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BotsTest {

    private static final Sleeper NO_WAIT = duration -> {};

    @Test
    void buyerGetsATicket() throws Exception {
        var system = new FakeTicketHit(10, false);

        assertThat(BuyerBot.run(bot(system, "bot-1", new LeakedPasses()))).isEqualTo(Outcome.CONFIRMED);
        assertThat(system.available()).isEqualTo(9);
    }

    @Test
    void buyerSeesSoldOut() throws Exception {
        var system = new FakeTicketHit(0, false);

        assertThat(BuyerBot.run(bot(system, "bot-1", new LeakedPasses()))).isEqualTo(Outcome.SOLD_OUT);
    }

    @Test
    void buyerRetriesUnavailablePayment() throws Exception {
        var system = new FakeTicketHit(10, false);
        system.failNextPayments(2);

        assertThat(BuyerBot.run(bot(system, "bot-1", new LeakedPasses()))).isEqualTo(Outcome.CONFIRMED);
    }

    @Test
    void buyerChecksTheHoldInsteadOfPayingTwiceAfterGatewayTimeout() throws Exception {
        var system = new FakeTicketHit(10, false);
        system.timeOutAfterAcceptingPayments();

        assertThat(BuyerBot.run(bot(system, "bot-1", new LeakedPasses()))).isEqualTo(Outcome.CONFIRMED);
        assertThat(system.payments()).isEqualTo(1);
    }

    @Test
    void buyerSeesDeclinedPayment() throws Exception {
        var system = new FakeTicketHit(10, false);
        system.declineAllPayments();

        assertThat(BuyerBot.run(bot(system, "bot-1", new LeakedPasses()))).isEqualTo(Outcome.DECLINED);
        assertThat(system.available()).isEqualTo(10);
    }

    @ParameterizedTest
    @EnumSource(Attack.class)
    void attacksAreBlockedByTheSystem(Attack attack) throws Exception {
        var system = new FakeTicketHit(10, false);
        var leaked = new LeakedPasses();
        leaked.leak("pass-bot-1");

        Outcome outcome = AbusiveBot.run(bot(system, "bot-666", leaked), attack);

        assertThat(outcome.name()).startsWith("BLOCKED_");
        assertThat(system.available()).isEqualTo(10);
    }

    // Sem as defesas, o ataque precisa aparecer como bem-sucedido: é o que
    // garante que o contador ATTACK_SUCCEEDED detecta uma falha de verdade.
    @ParameterizedTest
    @EnumSource(Attack.class)
    void attacksSucceedWhenTheSystemDoesNotDefend(Attack attack) throws Exception {
        var system = new FakeTicketHit(10, true);
        var leaked = new LeakedPasses();
        leaked.leak("pass-bot-1");

        assertThat(AbusiveBot.run(bot(system, "bot-666", leaked), attack)).isEqualTo(Outcome.ATTACK_SUCCEEDED);
    }

    private static BotContext bot(FakeTicketHit system, String botId, LeakedPasses leaked) {
        Instant now = Instant.now();
        return new BotContext(system, UUID.randomUUID(), botId, now, now.plus(Duration.ofMinutes(1)),
                Duration.ZERO, leaked, Clock.systemUTC(), NO_WAIT, problem -> {});
    }
}
