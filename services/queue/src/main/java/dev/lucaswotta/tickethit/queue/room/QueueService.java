package dev.lucaswotta.tickethit.queue.room;

import dev.lucaswotta.tickethit.queue.QueueProperties;
import dev.lucaswotta.tickethit.queue.pass.PassIssuer;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class QueueService {

    // SecureRandom: com um gerador previsível, quem conhecesse a semente saberia
    // a própria posição no sorteio antes da abertura.
    private final SecureRandom lottery = new SecureRandom();

    private final QueueRepository queues;
    private final PassIssuer passIssuer;
    private final QueueProperties properties;
    private final Clock clock;

    QueueService(QueueRepository queues, PassIssuer passIssuer, QueueProperties properties, Clock clock) {
        this.queues = queues;
        this.passIssuer = passIssuer;
        this.properties = properties;
        this.clock = clock;
    }

    public void create(UUID saleId, Instant opensAt, int admissionsPerSecond) {
        Instant expiresAt = opensAt.plus(properties.roomTtl());
        if (!queues.create(saleId, opensAt, admissionsPerSecond, expiresAt)) {
            throw new QueueAlreadyExistsException(saleId);
        }
    }

    /** Devolve true se o comprador entrou agora, false se já estava na fila ou já foi liberado. */
    public boolean join(UUID saleId, String customerId) {
        long result = queues.join(saleId, customerId, clock.instant(), lottery.nextDouble());
        if (result == -1) {
            throw new QueueNotFoundException(saleId);
        }
        return result == 1;
    }

    public Entry entry(UUID saleId, String customerId) {
        QueueSettings settings = queues.settings(saleId).orElseThrow(() -> new QueueNotFoundException(saleId));
        Instant now = clock.instant();
        var admittedAt = queues.admittedAt(saleId, customerId);
        if (admittedAt.isPresent()) {
            // Em segundos inteiros, como o exp do JWT: a API e o token informam o mesmo prazo.
            Instant passExpiresAt = admittedAt.get().plus(properties.pass().ttl()).truncatedTo(ChronoUnit.SECONDS);
            if (!now.isBefore(passExpiresAt)) {
                return new Entry(EntryStatus.PASS_EXPIRED, settings.opensAt(), null, null, null, null, passExpiresAt);
            }
            String pass = passIssuer.issue(saleId, customerId, admittedAt.get(), passExpiresAt);
            return new Entry(EntryStatus.ADMITTED, settings.opensAt(), null, null, null, pass, passExpiresAt);
        }
        long rank = queues.rank(saleId, customerId).orElseThrow(() -> new NotInQueueException(saleId, customerId));
        long waiting = queues.waitingCount(saleId);
        // Antes do sorteio a posição não sai: a nota aleatória já existe, mas
        // revelá-la antecipadamente deixaria o comprador saber o resultado.
        if (now.isBefore(settings.opensAt())) {
            return new Entry(EntryStatus.WAITING_DRAW, settings.opensAt(), null, waiting, null, null, null);
        }
        long estimatedWait = (rank + 1 + settings.admissionsPerSecond() - 1) / settings.admissionsPerSecond();
        return new Entry(EntryStatus.WAITING, settings.opensAt(), rank + 1, waiting, estimatedWait, null, null);
    }

    public QueueStats stats(UUID saleId) {
        QueueSettings settings = queues.settings(saleId).orElseThrow(() -> new QueueNotFoundException(saleId));
        return new QueueStats(
                saleId,
                settings.opensAt(),
                settings.admissionsPerSecond(),
                queues.waitingCount(saleId),
                queues.admittedCount(saleId));
    }

    /** Libera quem a taxa permite em cada fila ativa e devolve quantos saíram. */
    public int admitDue() {
        Instant now = clock.instant();
        int admitted = 0;
        for (String sale : queues.activeSales()) {
            UUID saleId = UUID.fromString(sale);
            if (queues.settings(saleId).isEmpty()) {
                queues.deactivate(saleId);
                continue;
            }
            List<String> released = queues.admit(saleId, now);
            admitted += released.size();
        }
        return admitted;
    }
}
