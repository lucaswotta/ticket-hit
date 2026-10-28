package dev.lucaswotta.tickethit.simulator.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import dev.lucaswotta.tickethit.simulator.FakeTicketHit;
import dev.lucaswotta.tickethit.simulator.SimulatorProperties;
import dev.lucaswotta.tickethit.simulator.bot.Sleeper;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

class RoomServiceTest {

    private static final RoomSettings HUNDRED_BOTS_FIFTY_TICKETS =
            new RoomSettings("ATOMIC", 50, 100, 50, Duration.ZERO);

    @Test
    void everyBotEndsWithAnOutcomeAndNoAttackSucceeds() {
        var system = new FakeTicketHit(0, false);
        var rooms = new RoomService(system, properties(3), Clock.systemUTC(), duration -> {});

        Room room = rooms.create("ana", HUNDRED_BOTS_FIFTY_TICKETS);

        await().atMost(Duration.ofSeconds(20)).until(() -> room.status() == Room.Status.FINISHED);
        var outcomes = room.stats().outcomes();
        assertThat(outcomes.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(100);
        assertThat(outcomes.get("CONFIRMED")).isEqualTo(50);
        assertThat(outcomes).doesNotContainKeys("ATTACK_SUCCEEDED", "ERROR");
        assertThat(outcomes.keySet().stream().filter(name -> name.startsWith("BLOCKED_"))).hasSize(4);
        assertThat(system.available()).isZero();
    }

    @Test
    void roomsBeyondTheLimitAreRefusedUntilOneFinishes() throws Exception {
        var release = new CountDownLatch(1);
        Sleeper heldBots = duration -> release.await();
        var rooms = new RoomService(new FakeTicketHit(0, false), properties(1), Clock.systemUTC(), heldBots);
        Room first = rooms.create("ana", HUNDRED_BOTS_FIFTY_TICKETS);

        assertThatThrownBy(() -> rooms.create("bia", HUNDRED_BOTS_FIFTY_TICKETS))
                .isInstanceOfSatisfying(RoomsFullException.class, full ->
                        // 100 bots a 50/s levam 2 s para sair da fila, mais 15 s para assentar.
                        assertThat(full.retryAfter()).isBetween(Duration.ofSeconds(15), Duration.ofSeconds(17)));

        release.countDown();
        await().atMost(Duration.ofSeconds(20)).until(() -> first.status() == Room.Status.FINISHED);
        assertThat(rooms.create("bia", HUNDRED_BOTS_FIFTY_TICKETS).owner()).isEqualTo("bia");
    }

    @Test
    void roomOfAnotherVisitorIsNotFound() {
        var rooms = new RoomService(new FakeTicketHit(0, false), properties(3), Clock.systemUTC(), duration -> {});
        Room room = rooms.create("ana", HUNDRED_BOTS_FIFTY_TICKETS);

        assertThatThrownBy(() -> rooms.find(room.id(), "bia")).isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void botsAboveTheLimitAreRefused() {
        var rooms = new RoomService(new FakeTicketHit(0, false), properties(3), Clock.systemUTC(), duration -> {});

        assertThatThrownBy(() -> rooms.create("ana", new RoomSettings("ATOMIC", 50, 1_001, 50, Duration.ZERO)))
                .isInstanceOf(TooManyBotsException.class);
    }

    private static SimulatorProperties properties(int maxRooms) {
        return new SimulatorProperties(
                URI.create("http://localhost"),
                maxRooms,
                1_000,
                0.05,
                Duration.ofMinutes(1),
                new SimulatorProperties.Defaults(500, 1_000, 50, Duration.ZERO),
                new SimulatorProperties.Reaction(Duration.ZERO, Duration.ZERO));
    }
}
