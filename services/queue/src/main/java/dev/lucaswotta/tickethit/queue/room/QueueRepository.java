package dev.lucaswotta.tickethit.queue.room;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

/**
 * Estado da fila no Redis. As chaves de uma venda levam o id entre chaves
 * ({@code queue:{id}:...}): num Redis Cluster, ficam no mesmo slot e os scripts
 * Lua podem tocar todas de uma vez.
 */
@Repository
class QueueRepository {

    private static final String ACTIVE = "queue:active";

    private static final RedisScript<Long> CREATE = script("create.lua", Long.class);
    private static final RedisScript<Long> JOIN = script("join.lua", Long.class);
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> ADMIT = script("admit.lua", List.class);

    private final StringRedisTemplate redis;

    QueueRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    boolean create(UUID saleId, Instant opensAt, int admissionsPerSecond, Instant expiresAt) {
        Long created = redis.execute(
                CREATE,
                List.of(meta(saleId)),
                millis(opensAt),
                String.valueOf(admissionsPerSecond),
                millis(expiresAt));
        if (created == 1) {
            redis.opsForSet().add(ACTIVE, saleId.toString());
        }
        return created == 1;
    }

    long join(UUID saleId, String customerId, Instant now, double lotteryScore) {
        return redis.execute(JOIN, keys(saleId), customerId, millis(now), String.valueOf(lotteryScore));
    }

    @SuppressWarnings("unchecked")
    List<String> admit(UUID saleId, Instant now) {
        return redis.execute(ADMIT, keys(saleId), millis(now));
    }

    Optional<QueueSettings> settings(UUID saleId) {
        Map<Object, Object> fields = redis.opsForHash().entries(meta(saleId));
        if (fields.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new QueueSettings(
                Instant.ofEpochMilli(Long.parseLong((String) fields.get("opensAt"))),
                Integer.parseInt((String) fields.get("admissionsPerSecond"))));
    }

    Optional<Long> rank(UUID saleId, String customerId) {
        return Optional.ofNullable(redis.opsForZSet().rank(waiting(saleId), customerId));
    }

    long waitingCount(UUID saleId) {
        Long count = redis.opsForZSet().zCard(waiting(saleId));
        return count == null ? 0 : count;
    }

    long admittedCount(UUID saleId) {
        return redis.opsForHash().size(admitted(saleId));
    }

    Optional<Instant> admittedAt(UUID saleId, String customerId) {
        Object value = redis.opsForHash().get(admitted(saleId), customerId);
        return Optional.ofNullable(value).map(millis -> Instant.ofEpochMilli(Long.parseLong((String) millis)));
    }

    Set<String> activeSales() {
        return redis.opsForSet().members(ACTIVE);
    }

    void deactivate(UUID saleId) {
        redis.opsForSet().remove(ACTIVE, saleId.toString());
    }

    private static List<String> keys(UUID saleId) {
        return List.of(meta(saleId), waiting(saleId), admitted(saleId));
    }

    private static String meta(UUID saleId) {
        return "queue:{" + saleId + "}:meta";
    }

    private static String waiting(UUID saleId) {
        return "queue:{" + saleId + "}:waiting";
    }

    private static String admitted(UUID saleId) {
        return "queue:{" + saleId + "}:admitted";
    }

    private static String millis(Instant instant) {
        return String.valueOf(instant.toEpochMilli());
    }

    private static <T> RedisScript<T> script(String name, Class<T> resultType) {
        return RedisScript.of(new ClassPathResource("scripts/" + name), resultType);
    }
}
