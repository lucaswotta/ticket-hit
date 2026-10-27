package dev.lucaswotta.tickethit.ticketing.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Relógio que só anda quando o teste manda, para testar prazos sem esperar. */
public class MutableClock extends Clock {

    // Com nanossegundos, mais preciso que o timestamptz do PostgreSQL, para os
    // testes pegarem diferença entre o valor em memória e o gravado.
    private volatile Instant now = Instant.parse("2026-10-27T21:00:00.123456789Z");

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
