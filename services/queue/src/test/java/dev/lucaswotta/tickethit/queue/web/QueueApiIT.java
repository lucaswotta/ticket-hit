package dev.lucaswotta.tickethit.queue.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.lucaswotta.tickethit.queue.support.IntegrationTest;
import dev.lucaswotta.tickethit.queue.support.MutableClock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class QueueApiIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    MutableClock clock;

    @Test
    void createsQueueAndRejectsDuplicate() {
        UUID saleId = UUID.randomUUID();

        assertThat(createQueue(saleId, 10))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.admissionsPerSecond")
                .isEqualTo(10);
        assertThat(createQueue(saleId, 10))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("QUEUE_ALREADY_EXISTS");
    }

    @Test
    void joinIsCreatedOnceAndOkAfterwards() {
        UUID saleId = UUID.randomUUID();
        createQueue(saleId, 10);

        assertThat(join(saleId, "ana"))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("WAITING_DRAW");
        assertThat(join(saleId, "ana")).hasStatus(HttpStatus.OK);
    }

    @Test
    void unknownBuyerAndUnknownQueueAreNotFound() {
        UUID saleId = UUID.randomUUID();
        createQueue(saleId, 10);

        assertThat(mvc.get().uri("/api/queues/{id}/entries/me", saleId).header("X-Customer-Id", "ninguem"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("NOT_IN_QUEUE");
        assertThat(join(UUID.randomUUID(), "ana"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("QUEUE_NOT_FOUND");
    }

    @Test
    void joinWithoutCustomerIsBadRequest() {
        UUID saleId = UUID.randomUUID();
        createQueue(saleId, 10);

        assertThat(mvc.post().uri("/api/queues/{id}/entries", saleId)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void admissionRateIsLimited() {
        assertThat(createQueue(UUID.randomUUID(), 5_000)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    private MvcTestResult createQueue(UUID saleId, int admissionsPerSecond) {
        return mvc.post()
                .uri("/api/queues")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"saleId": "%s", "opensAt": "%s", "admissionsPerSecond": %d}
                        """.formatted(saleId, clock.instant().plus(Duration.ofMinutes(5)), admissionsPerSecond))
                .exchange();
    }

    private MvcTestResult join(UUID saleId, String customerId) {
        return mvc.post()
                .uri("/api/queues/{id}/entries", saleId)
                .header("X-Customer-Id", customerId)
                .exchange();
    }
}
