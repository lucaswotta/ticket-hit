package dev.lucaswotta.tickethit.simulator.room;

import dev.lucaswotta.tickethit.simulator.api.Reply;
import dev.lucaswotta.tickethit.simulator.api.TicketHitApi;
import java.time.Instant;
import java.util.UUID;

/** Conta o status de cada resposta que os bots recebem, sem que cada bot precise lembrar disso. */
final class RecordingApi implements TicketHitApi {

    private final TicketHitApi api;
    private final RoomStats stats;

    RecordingApi(TicketHitApi api, RoomStats stats) {
        this.api = api;
        this.stats = stats;
    }

    @Override
    public UUID openSale(String name, int tickets, String inventoryStrategy) {
        return api.openSale(name, tickets, inventoryStrategy);
    }

    @Override
    public void openQueue(UUID saleId, Instant opensAt, int admissionsPerSecond) {
        api.openQueue(saleId, opensAt, admissionsPerSecond);
    }

    @Override
    public Reply sale(UUID saleId) {
        return record(api.sale(saleId));
    }

    @Override
    public Reply join(UUID saleId, String bot) {
        return record(api.join(saleId, bot));
    }

    @Override
    public Reply entry(UUID saleId, String bot) {
        return record(api.entry(saleId, bot));
    }

    @Override
    public Reply hold(UUID saleId, String bot, String pass, int quantity) {
        return record(api.hold(saleId, bot, pass, quantity));
    }

    @Override
    public Reply pay(UUID holdId, String bot) {
        return record(api.pay(holdId, bot));
    }

    @Override
    public Reply holdStatus(UUID holdId, String bot) {
        return record(api.holdStatus(holdId, bot));
    }

    private Reply record(Reply reply) {
        stats.httpStatus(reply.status());
        return reply;
    }
}
