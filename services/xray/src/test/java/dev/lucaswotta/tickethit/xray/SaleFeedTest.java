package dev.lucaswotta.tickethit.xray;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SaleFeedTest {

    @Test
    void backlogKeepsOnlyTheLatestEvents() {
        var feed = new SaleFeed(3, Instant.now());

        for (int i = 1; i <= 5; i++) {
            feed.accept("{\"n\": %d}".formatted(i), Instant.now());
        }

        assertThat(feed.backlog()).extracting(SaleFeed.Event::sequence).containsExactly(3L, 4L, 5L);
    }
}
