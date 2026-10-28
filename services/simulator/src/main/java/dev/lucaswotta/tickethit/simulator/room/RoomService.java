package dev.lucaswotta.tickethit.simulator.room;

import dev.lucaswotta.tickethit.simulator.SimulatorProperties;
import dev.lucaswotta.tickethit.simulator.api.TicketHitApi;
import dev.lucaswotta.tickethit.simulator.bot.AbusiveBot;
import dev.lucaswotta.tickethit.simulator.bot.Attack;
import dev.lucaswotta.tickethit.simulator.bot.BotContext;
import dev.lucaswotta.tickethit.simulator.bot.BuyerBot;
import dev.lucaswotta.tickethit.simulator.bot.LeakedPasses;
import dev.lucaswotta.tickethit.simulator.bot.Outcome;
import dev.lucaswotta.tickethit.simulator.bot.Sleeper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RoomService {

    private static final Logger log = LoggerFactory.getLogger(RoomService.class);
    private static final Duration KEEP_FINISHED = Duration.ofMinutes(30);
    // Depois de todos liberados: reação, pagamento e confirmação dos últimos bots.
    private static final Duration SETTLING = Duration.ofSeconds(15);
    private static final Duration MIN_RETRY = Duration.ofSeconds(5);

    private final Map<UUID, Room> rooms = new ConcurrentHashMap<>();
    private final AtomicInteger running = new AtomicInteger();
    // Uma thread virtual por bot: cada um passa quase todo o tempo esperando a fila.
    private final ExecutorService bots = Executors.newVirtualThreadPerTaskExecutor();

    private final TicketHitApi api;
    private final SimulatorProperties properties;
    private final Clock clock;
    private final Sleeper sleeper;

    RoomService(TicketHitApi api, SimulatorProperties properties, Clock clock, Sleeper sleeper) {
        this.api = api;
        this.properties = properties;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    public Room create(String owner, RoomSettings settings) {
        if (settings.bots() > properties.maxBots()) {
            throw new TooManyBotsException(settings.bots(), properties.maxBots());
        }
        if (running.incrementAndGet() > properties.maxRooms()) {
            running.decrementAndGet();
            throw new RoomsFullException(properties.maxRooms(), untilARoomEnds());
        }
        try {
            forgetOldRooms();
            UUID roomId = UUID.randomUUID();
            UUID saleId = api.openSale("Sala " + roomId.toString().substring(0, 8), settings.tickets(),
                    settings.inventoryStrategy());
            Instant opensAt = clock.instant().plus(settings.lobby());
            api.openQueue(saleId, opensAt, settings.admissionsPerSecond());
            Room room = new Room(roomId, owner, saleId, settings, opensAt);
            rooms.put(roomId, room);
            startBots(room);
            return room;
        } catch (RuntimeException failure) {
            running.decrementAndGet();
            throw failure;
        }
    }

    public Room find(UUID roomId, String owner) {
        Room room = rooms.get(roomId);
        if (room == null || !room.owner().equals(owner)) {
            throw new RoomNotFoundException(roomId);
        }
        return room;
    }

    private void startBots(Room room) {
        TicketHitApi recording = new RecordingApi(api, room.stats());
        LeakedPasses leaked = new LeakedPasses();
        Instant deadline = room.opensAt().plus(properties.roomTimeout());
        // Faixa de ids própria por sala: bots de salas simultâneas não dividem
        // o balde do rate limit nem o lugar na fila.
        int firstBot = ThreadLocalRandom.current().nextInt(999) * 1_000;
        int abusive = (int) Math.round(room.settings().bots() * properties.abusiveShare());
        Attack[] attacks = Attack.values();

        List<CompletableFuture<Void>> botRuns = new ArrayList<>();
        for (int i = 0; i < room.settings().bots(); i++) {
            var context = new BotContext(recording, room.saleId(), "bot-" + (firstBot + i), room.opensAt(), deadline,
                    reaction(), leaked, clock, sleeper, room.stats()::error);
            Attack attack = i < abusive ? attacks[i % attacks.length] : null;
            botRuns.add(CompletableFuture.runAsync(() -> runBot(room, context, attack), bots));
        }
        CompletableFuture.allOf(botRuns.toArray(CompletableFuture[]::new))
                .whenComplete((done, failure) -> finish(room));
    }

    private void runBot(Room room, BotContext context, Attack attack) {
        try {
            // Chegadas espalhadas pelo lobby, todas antes da abertura: todos entram no sorteio.
            long lobbyMillis = room.settings().lobby().toMillis();
            sleeper.sleep(Duration.ofMillis(ThreadLocalRandom.current().nextLong(Math.max(1, lobbyMillis * 8 / 10))));
            Outcome outcome = attack == null ? BuyerBot.run(context) : AbusiveBot.run(context, attack);
            room.stats().outcome(outcome);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            room.stats().outcome(Outcome.GAVE_UP);
        } catch (RuntimeException failure) {
            room.stats().outcome(Outcome.ERROR);
            room.stats().error(failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    private void finish(Room room) {
        room.finish(clock.instant());
        running.decrementAndGet();
        log.info("Sala {} encerrada: {}", room.id(), room.stats().outcomes());
    }

    // Fim estimado da sala que termina primeiro: abertura, tempo de liberar todos
    // os bots e o tempo de assentar, nunca depois do prazo da sala.
    private Duration untilARoomEnds() {
        Instant now = clock.instant();
        return rooms.values().stream()
                .filter(room -> room.status() == Room.Status.RUNNING)
                .map(room -> {
                    RoomSettings settings = room.settings();
                    long admitSeconds = (settings.bots() + settings.admissionsPerSecond() - 1) / settings.admissionsPerSecond();
                    Instant estimated = room.opensAt().plusSeconds(admitSeconds).plus(SETTLING);
                    Instant deadline = room.opensAt().plus(properties.roomTimeout());
                    return Duration.between(now, estimated.isBefore(deadline) ? estimated : deadline);
                })
                .min(Duration::compareTo)
                .filter(remaining -> remaining.compareTo(MIN_RETRY) > 0)
                .orElse(MIN_RETRY);
    }

    private Duration reaction() {
        long min = properties.reaction().min().toMillis();
        long max = properties.reaction().max().toMillis();
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(min, max + 1));
    }

    private void forgetOldRooms() {
        Instant limit = clock.instant().minus(KEEP_FINISHED);
        rooms.values().removeIf(room -> room.finishedAt() != null && room.finishedAt().isBefore(limit));
    }
}
