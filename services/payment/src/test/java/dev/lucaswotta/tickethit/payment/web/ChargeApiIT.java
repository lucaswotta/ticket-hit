package dev.lucaswotta.tickethit.payment.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import dev.lucaswotta.tickethit.payment.charge.FakeGateway;
import dev.lucaswotta.tickethit.payment.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class ChargeApiIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    FakeGateway gateway;

    @AfterEach
    void restoreGateway() {
        gateway.unavailableRate(0);
    }

    @Test
    void acceptsChargeAndReplaysSameKey() throws Exception {
        UUID holdId = UUID.randomUUID();

        MvcTestResult first = charge(holdId.toString(), holdId);
        MvcTestResult second = charge(holdId.toString(), holdId);

        assertThat(first).hasStatus(HttpStatus.ACCEPTED);
        assertThat(first).headers().hasValue("Idempotent-Replayed", "false");
        assertThat(first).bodyJson().extractingPath("$.status").isEqualTo("PENDING");
        assertThat(second).hasStatus(HttpStatus.ACCEPTED);
        assertThat(second).headers().hasValue("Idempotent-Replayed", "true");
        assertThat(idOf(second)).isEqualTo(idOf(first));
        assertThat(mvc.get().uri("/api/charges/" + idOf(first))).hasStatusOk();
    }

    @Test
    void keyReusedForAnotherHoldIsUnprocessable() {
        String key = UUID.randomUUID().toString();
        charge(key, UUID.randomUUID());

        assertThat(charge(key, UUID.randomUUID()))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void missingIdempotencyKeyIsBadRequest() {
        MvcTestResult result = mvc.post()
                .uri("/api/charges")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"holdId": "%s", "customerId": "ana"}
                        """.formatted(UUID.randomUUID()))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unavailableGatewayAnswers503WithRetryAfter() {
        gateway.unavailableRate(1);

        MvcTestResult result = charge(UUID.randomUUID().toString(), UUID.randomUUID());

        assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(result).headers().hasValue("Retry-After", "1");
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("PAYMENT_UNAVAILABLE");
    }

    private MvcTestResult charge(String key, UUID holdId) {
        return mvc.post()
                .uri("/api/charges")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"holdId": "%s", "customerId": "ana"}
                        """.formatted(holdId))
                .exchange();
    }

    private static String idOf(MvcTestResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}
