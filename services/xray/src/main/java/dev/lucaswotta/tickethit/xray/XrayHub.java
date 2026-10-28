package dev.lucaswotta.tickethit.xray;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
class XrayHub {

    private final Map<UUID, SaleFeed> feeds = new ConcurrentHashMap<>();
    private final XrayProperties properties;
    private final Clock clock;

    XrayHub(XrayProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    void accept(UUID saleId, String json) {
        feed(saleId).accept(json, clock.instant());
    }

    SseEmitter subscribe(UUID saleId) {
        var stream = new SseEmitter(properties.streamTimeout().toMillis());
        SaleFeed feed = feed(saleId);
        if (!feed.subscribe(stream, properties.maxStreamsPerSale(), clock.instant())) {
            throw new TooManyStreamsException(saleId, properties.maxStreamsPerSale());
        }
        stream.onCompletion(() -> feed.unsubscribe(stream));
        stream.onTimeout(() -> feed.unsubscribe(stream));
        stream.onError(error -> feed.unsubscribe(stream));
        return stream;
    }

    int backlogOf(UUID saleId) {
        SaleFeed feed = feeds.get(saleId);
        return feed == null ? 0 : feed.backlog().size();
    }

    @Scheduled(fixedDelayString = "${xray.heartbeat-interval}")
    void heartbeatAndForget() {
        var limit = clock.instant().minus(properties.idleFeedTtl());
        feeds.values().forEach(SaleFeed::heartbeat);
        feeds.values().removeIf(feed -> feed.idleSince(limit));
    }

    private SaleFeed feed(UUID saleId) {
        return feeds.computeIfAbsent(saleId, id -> new SaleFeed(properties.backlogSize(), clock.instant()));
    }
}
