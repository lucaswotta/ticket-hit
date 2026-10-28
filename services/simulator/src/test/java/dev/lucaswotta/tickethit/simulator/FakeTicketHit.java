package dev.lucaswotta.tickethit.simulator;

import dev.lucaswotta.tickethit.simulator.api.Reply;
import dev.lucaswotta.tickethit.simulator.api.TicketHitApi;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

/**
 * O sistema inteiro em memória, com as regras que os bots precisam encontrar:
 * fila que libera na abertura, passe por comprador, estoque, recusa de
 * pagamento e rate limit. Com {@code permissive}, as defesas ficam desligadas.
 */
public class FakeTicketHit implements TicketHitApi {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int RATE_LIMIT = 10;

    private final boolean permissive;
    private final Map<String, Integer> requestsPerBot = new HashMap<>();
    private final Map<UUID, String> holdOwners = new HashMap<>();
    private final Set<UUID> declined = new HashSet<>();
    private final Set<UUID> paid = new HashSet<>();
    private int payments;
    private int available;
    private Instant opensAt = Instant.EPOCH;
    private int unavailablePayments;
    private boolean timeoutAfterAccepting;
    private boolean declineAll;

    public FakeTicketHit(int tickets, boolean permissive) {
        this.available = tickets;
        this.permissive = permissive;
    }

    public synchronized void failNextPayments(int count) {
        unavailablePayments = count;
    }

    /** O pagamento segue, mas a resposta vem como 504, como o gateway faz ao desistir de esperar. */
    public synchronized void timeOutAfterAcceptingPayments() {
        timeoutAfterAccepting = true;
    }

    public synchronized void declineAllPayments() {
        declineAll = true;
    }

    public synchronized int available() {
        return available;
    }

    @Override
    public synchronized UUID openSale(String name, int tickets, String inventoryStrategy) {
        available = tickets;
        return UUID.randomUUID();
    }

    @Override
    public synchronized void openQueue(UUID saleId, Instant opensAt, int admissionsPerSecond) {
        this.opensAt = opensAt;
    }

    @Override
    public synchronized Reply sale(UUID saleId) {
        return reply(200, "{\"availableTickets\": %d}".formatted(available));
    }

    @Override
    public synchronized Reply join(UUID saleId, String bot) {
        return reply(201, "{\"status\": \"WAITING_DRAW\"}");
    }

    @Override
    public synchronized Reply entry(UUID saleId, String bot) {
        if (!permissive && requestsPerBot.merge(bot, 1, Integer::sum) > RATE_LIMIT) {
            return reply(429, "{}");
        }
        if (Instant.now().isBefore(opensAt)) {
            return reply(200, "{\"status\": \"WAITING_DRAW\"}");
        }
        return reply(200, "{\"status\": \"ADMITTED\", \"pass\": \"pass-%s\"}".formatted(bot));
    }

    @Override
    public synchronized Reply hold(UUID saleId, String bot, String pass, int quantity) {
        if (!permissive && pass == null) {
            return reply(400, "{}");
        }
        if (!permissive && !("pass-" + bot).equals(pass)) {
            return reply(403, "{\"code\": \"INVALID_PASS\"}");
        }
        if (available < quantity) {
            return reply(409, "{\"code\": \"SOLD_OUT\"}");
        }
        available -= quantity;
        UUID holdId = UUID.randomUUID();
        holdOwners.put(holdId, bot);
        return reply(201, "{\"id\": \"%s\"}".formatted(holdId));
    }

    public synchronized int payments() {
        return payments;
    }

    @Override
    public synchronized Reply pay(UUID holdId, String bot) {
        payments++;
        if (unavailablePayments > 0) {
            unavailablePayments--;
            return reply(503, "{\"code\": \"PAYMENT_UNAVAILABLE\"}");
        }
        if (declineAll) {
            declined.add(holdId);
            available++;
        }
        paid.add(holdId);
        return reply(timeoutAfterAccepting ? 504 : 202, "{}");
    }

    @Override
    public synchronized Reply holdStatus(UUID holdId, String bot) {
        if (!bot.equals(holdOwners.get(holdId))) {
            return reply(404, "{}");
        }
        String status = !paid.contains(holdId) ? "HELD" : declined.contains(holdId) ? "RELEASED" : "CONFIRMED";
        return reply(200, "{\"status\": \"%s\"}".formatted(status));
    }

    private static Reply reply(int status, String json) {
        return new Reply(status, JSON.readTree(json));
    }
}
