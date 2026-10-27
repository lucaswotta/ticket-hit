package dev.lucaswotta.tickethit.payment.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** Relógio que só anda quando o teste manda, para testar prazos sem esperar. */
public class MutableClock extends Clock {

    // Parte do horário real, porque o Redis expira chaves pelo próprio relógio,
    // e tem nanossegundos, mais preciso que o timestamptz do PostgreSQL, para os
    // testes pegarem diferença entre o valor em memória e o gravado.
    private volatile Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS).plusNanos(123_456_789);

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException();
    }
}
