package dev.lucaswotta.tickethit.xray;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Eventos recentes de uma venda e os navegadores conectados a ela. Tudo sob o
 * mesmo monitor: quem conecta recebe o histórico e passa a receber ao vivo sem
 * buraco nem repetição entre um e outro.
 */
final class SaleFeed {

    record Event(long sequence, String json) {}

    private final int backlogSize;
    private final Deque<Event> backlog = new ArrayDeque<>();
    private final List<SseEmitter> streams = new ArrayList<>();
    private long nextSequence = 1;
    private Instant lastActivity;

    SaleFeed(int backlogSize, Instant now) {
        this.backlogSize = backlogSize;
        this.lastActivity = now;
    }

    synchronized void accept(String json, Instant now) {
        Event event = new Event(nextSequence++, json);
        backlog.addLast(event);
        if (backlog.size() > backlogSize) {
            backlog.removeFirst();
        }
        lastActivity = now;
        streams.removeIf(stream -> !send(stream, event));
    }

    synchronized boolean subscribe(SseEmitter stream, int maxStreams, Instant now) {
        if (streams.size() >= maxStreams) {
            return false;
        }
        for (Event event : backlog) {
            if (!send(stream, event)) {
                return true;
            }
        }
        streams.add(stream);
        lastActivity = now;
        return true;
    }

    synchronized void unsubscribe(SseEmitter stream) {
        streams.remove(stream);
    }

    // Comentário SSE: mantém a conexão viva em proxies que fecham conexão parada.
    synchronized void heartbeat() {
        streams.removeIf(stream -> {
            try {
                stream.send(SseEmitter.event().comment("heartbeat"));
                return false;
            } catch (IOException | IllegalStateException gone) {
                return true;
            }
        });
    }

    synchronized boolean idleSince(Instant limit) {
        return streams.isEmpty() && lastActivity.isBefore(limit);
    }

    synchronized List<Event> backlog() {
        return List.copyOf(backlog);
    }

    // Navegador que fechou a aba falha no envio e sai da lista.
    private static boolean send(SseEmitter stream, Event event) {
        try {
            stream.send(SseEmitter.event().id(Long.toString(event.sequence())).name("telemetry").data(event.json()));
            return true;
        } catch (IOException | IllegalStateException gone) {
            return false;
        }
    }
}
