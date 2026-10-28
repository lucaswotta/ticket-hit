package dev.lucaswotta.tickethit.queue.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import dev.lucaswotta.tickethit.queue.support.IntegrationTest;
import dev.lucaswotta.tickethit.queue.support.MutableClock;
import dev.lucaswotta.tickethit.queue.support.TelemetryReader;
import dev.lucaswotta.tickethit.queue.support.TestPassKeys;
import java.security.KeyFactory;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@IntegrationTest
class QueueServiceIT {

    private static final Duration UNTIL_OPENING = Duration.ofMinutes(5);

    @Autowired
    QueueService queueService;

    @Autowired
    MutableClock clock;

    @Autowired
    KafkaConnectionDetails kafka;

    @Test
    void positionIsHiddenUntilTheDraw() {
        UUID saleId = newQueue(10);
        queueService.join(saleId, "ana");

        Entry entry = queueService.entry(saleId, "ana");

        assertThat(entry.status()).isEqualTo(EntryStatus.WAITING_DRAW);
        assertThat(entry.position()).isNull();
        assertThat(entry.waiting()).isEqualTo(1);
    }

    // Ordem de chegada 0, 1, 2... Com sorteio, a ordem na fila é uma permutação
    // qualquer; a chance de sair idêntica à de chegada com 200 pessoas é 1/200!.
    @Test
    void drawShufflesEveryoneWhoArrivedBeforeOpening() {
        UUID saleId = newQueue(10);
        List<String> arrival = IntStream.range(0, 200).mapToObj(i -> "c" + i).toList();
        arrival.forEach(customer -> queueService.join(saleId, customer));
        clock.advance(UNTIL_OPENING);

        List<String> queueOrder = arrival.stream()
                .sorted(Comparator.comparingLong(customer -> queueService.entry(saleId, customer).position()))
                .toList();

        assertThat(queueOrder).containsExactlyInAnyOrderElementsOf(arrival).isNotEqualTo(arrival);
        assertThat(arrival.stream().map(customer -> queueService.entry(saleId, customer).position()))
                .containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, 200).mapToObj(i -> (long) i).toList());
    }

    @Test
    void arrivalsAfterOpeningQueueBehindTheDrawInArrivalOrder() {
        UUID saleId = newQueue(10);
        queueService.join(saleId, "sorteado-1");
        queueService.join(saleId, "sorteado-2");
        clock.advance(UNTIL_OPENING);
        queueService.join(saleId, "atrasado-1");
        clock.advance(Duration.ofMillis(5));
        queueService.join(saleId, "atrasado-2");

        assertThat(queueService.entry(saleId, "atrasado-1").position()).isEqualTo(3);
        assertThat(queueService.entry(saleId, "atrasado-2").position()).isEqualTo(4);
    }

    @Test
    void joiningAgainKeepsThePlace() {
        UUID saleId = newQueue(10);
        clock.advance(UNTIL_OPENING);
        assertThat(queueService.join(saleId, "ana")).isTrue();
        queueService.join(saleId, "bia");

        assertThat(queueService.join(saleId, "ana")).isFalse();

        assertThat(queueService.entry(saleId, "ana").position()).isEqualTo(1);
        assertThat(queueService.stats(saleId).waiting()).isEqualTo(2);
    }

    @Test
    void admissionFollowsTheRate() {
        UUID saleId = newQueue(10);
        IntStream.range(0, 100).forEach(i -> queueService.join(saleId, "c" + i));
        clock.advance(UNTIL_OPENING);

        clock.advance(Duration.ofMillis(1_000));
        queueService.admitDue();
        assertThat(queueService.stats(saleId).admitted()).isEqualTo(10);

        clock.advance(Duration.ofMillis(1_500));
        queueService.admitDue();
        assertThat(queueService.stats(saleId).admitted()).isEqualTo(25);
    }

    // Várias instâncias chamando a liberação no mesmo instante, como num deploy
    // com réplicas. O token bucket no Lua precisa liberar só o que a taxa permite.
    @Test
    void concurrentAdmissionsDoNotExceedTheRate() throws Exception {
        UUID saleId = newQueue(10);
        IntStream.range(0, 100).forEach(i -> queueService.join(saleId, "c" + i));
        clock.advance(UNTIL_OPENING.plusSeconds(3));

        var start = new CountDownLatch(1);
        List<Future<?>> instances = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 8; i++) {
                instances.add(executor.submit(() -> {
                    start.await();
                    return queueService.admitDue();
                }));
            }
            start.countDown();
            for (Future<?> instance : instances) {
                instance.get();
            }
        }

        assertThat(queueService.stats(saleId).admitted()).isEqualTo(30);
    }

    @Test
    void emptyQueueDoesNotAccumulateAdmissions() {
        UUID saleId = newQueue(10);
        clock.advance(UNTIL_OPENING.plusSeconds(60));
        queueService.admitDue();
        IntStream.range(0, 100).forEach(i -> queueService.join(saleId, "c" + i));

        clock.advance(Duration.ofMillis(1_000));
        queueService.admitDue();

        assertThat(queueService.stats(saleId).admitted()).isEqualTo(10);
    }

    // Consultas da própria posição enquanto a liberação roda, como os bots fazem.
    // Quem está saindo da fila precisa aparecer como esperando ou liberado,
    // nunca como fora da fila.
    @Test
    void buyerIsNeverMissingWhileBeingAdmitted() throws Exception {
        UUID saleId = newQueue(1_000);
        List<String> buyers = IntStream.range(0, 300).mapToObj(i -> "c" + i).toList();
        buyers.forEach(customer -> queueService.join(saleId, customer));
        clock.advance(UNTIL_OPENING);

        var missing = new java.util.concurrent.atomic.AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var admissions = executor.submit(() -> {
                for (int i = 0; i < 300; i++) {
                    clock.advance(Duration.ofMillis(1));
                    queueService.admitDue();
                }
                return null;
            });
            List<Future<?>> readers = new ArrayList<>();
            for (int reader = 0; reader < 8; reader++) {
                readers.add(executor.submit(() -> {
                    while (!admissions.isDone()) {
                        for (String customer : buyers) {
                            try {
                                queueService.entry(saleId, customer);
                            } catch (NotInQueueException e) {
                                missing.incrementAndGet();
                            }
                        }
                    }
                    return null;
                }));
            }
            admissions.get();
            for (Future<?> reader : readers) {
                reader.get();
            }
        }

        assertThat(missing).hasValue(0);
    }

    @Test
    void admittedBuyerGetsAPassSignedForTicketing() throws Exception {
        UUID saleId = newQueue(10);
        queueService.join(saleId, "ana");
        clock.advance(UNTIL_OPENING.plusSeconds(1));
        queueService.admitDue();

        Entry entry = queueService.entry(saleId, "ana");

        assertThat(entry.status()).isEqualTo(EntryStatus.ADMITTED);
        Jwt pass = decoder().decode(entry.pass());
        assertThat(pass.getSubject()).isEqualTo("ana");
        assertThat(pass.getClaimAsString("sid")).isEqualTo(saleId.toString());
        assertThat(pass.getAudience()).containsExactly("ticketing");
        assertThat(pass.getExpiresAt()).isEqualTo(entry.passExpiresAt());
        assertThat(decoder().decode(queueService.entry(saleId, "ana").pass()).getId()).isEqualTo(pass.getId());
    }

    @Test
    void joinAndAdmissionShowUpInTelemetry() {
        UUID saleId = newQueue(10);
        queueService.join(saleId, "bot-1");
        clock.advance(UNTIL_OPENING.plusSeconds(1));
        queueService.admitDue();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(
                        TelemetryReader.eventsOf(kafka.getBootstrapServers(), saleId))
                .extracting(event -> event.get("type").asString() + ":" + event.get("customerId").asString())
                .containsExactly("QUEUE_JOINED:bot-1", "QUEUE_ADMITTED:bot-1"));
    }

    @Test
    void passExpiresAfterItsTtl() {
        UUID saleId = newQueue(10);
        queueService.join(saleId, "ana");
        clock.advance(UNTIL_OPENING.plusSeconds(1));
        queueService.admitDue();
        clock.advance(Duration.ofMinutes(2));

        assertThat(queueService.entry(saleId, "ana").status()).isEqualTo(EntryStatus.PASS_EXPIRED);
    }

    @Test
    void unknownQueueAndUnknownBuyerAreRejected() {
        UUID saleId = newQueue(10);

        assertThatThrownBy(() -> queueService.join(UUID.randomUUID(), "ana")).isInstanceOf(QueueNotFoundException.class);
        assertThatThrownBy(() -> queueService.entry(saleId, "ninguem")).isInstanceOf(NotInQueueException.class);
    }

    private UUID newQueue(int admissionsPerSecond) {
        UUID saleId = UUID.randomUUID();
        // Abertura no futuro em milissegundos inteiros: o Redis guarda o horário em ms.
        Instant opensAt = clock.instant().plus(UNTIL_OPENING).truncatedTo(ChronoUnit.MILLIS);
        queueService.create(saleId, opensAt, admissionsPerSecond);
        return saleId;
    }

    // Verifica como o ticketing vai verificar: só a chave pública, ES256 e o
    // prazo conferido pelo relógio do teste.
    private NimbusJwtDecoder decoder() throws Exception {
        var publicKey = (ECPublicKey) KeyFactory.getInstance("EC")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(TestPassKeys.PUBLIC_KEY)));
        var jwks = new ImmutableJWKSet<>(
                new JWKSet(new ECKey.Builder(Curve.P_256, publicKey).keyIDFromThumbprint().build()));
        var decoder = NimbusJwtDecoder.withJwkSource(jwks).jwsAlgorithm(SignatureAlgorithm.ES256).build();
        var timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        decoder.setJwtValidator(timestamps);
        return decoder;
    }
}
