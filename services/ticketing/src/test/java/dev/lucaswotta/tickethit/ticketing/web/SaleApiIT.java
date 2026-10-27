package dev.lucaswotta.tickethit.ticketing.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import dev.lucaswotta.tickethit.ticketing.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class SaleApiIT {

    @Autowired
    MockMvcTester mvc;

    @Test
    void opensSaleAndHoldsTickets() throws Exception {
        MvcTestResult opened = post("/api/sales", """
                {"name": "Noite 1", "totalTickets": 3}
                """);
        assertThat(opened).hasStatus(HttpStatus.CREATED);
        String saleId = JsonPath.read(opened.getResponse().getContentAsString(), "$.id");
        assertThat(opened).headers().hasValue("Location", "http://localhost/api/sales/" + saleId);
        assertThat(opened).bodyJson().extractingPath("$.inventoryStrategy").isEqualTo("ATOMIC");

        MvcTestResult held = post("/api/sales/" + saleId + "/holds", """
                {"customerId": "ana", "quantity": 2}
                """);
        assertThat(held).hasStatus(HttpStatus.CREATED);
        assertThat(held).bodyJson().extractingPath("$.status").isEqualTo("HELD");
        String holdId = JsonPath.read(held.getResponse().getContentAsString(), "$.id");

        assertThat(mvc.get().uri("/api/holds/" + holdId)).hasStatusOk();
        assertThat(mvc.get().uri("/api/sales/" + saleId))
                .bodyJson()
                .extractingPath("$.availableTickets")
                .isEqualTo(1);
    }

    @Test
    void soldOutIsConflict() throws Exception {
        String saleId = openSale(1);
        post("/api/sales/" + saleId + "/holds", """
                {"customerId": "ana", "quantity": 1}
                """);

        MvcTestResult result = post("/api/sales/" + saleId + "/holds", """
                {"customerId": "bia", "quantity": 1}
                """);

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("SOLD_OUT");
    }

    @Test
    void quantityAboveLimitIsUnprocessable() throws Exception {
        String saleId = openSale(10);

        MvcTestResult result = post("/api/sales/" + saleId + "/holds", """
                {"customerId": "ana", "quantity": 5}
                """);

        assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_QUANTITY");
    }

    @Test
    void malformedRequestIsBadRequest() throws Exception {
        String saleId = openSale(10);

        MvcTestResult result = post("/api/sales/" + saleId + "/holds", """
                {"quantity": 1}
                """);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void unknownSaleIsNotFound() {
        assertThat(mvc.get().uri("/api/sales/" + UUID.randomUUID()))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("NOT_FOUND");
    }

    private String openSale(int totalTickets) throws Exception {
        MvcTestResult opened = post("/api/sales", """
                {"name": "Noite 1", "totalTickets": %d}
                """.formatted(totalTickets));
        return JsonPath.read(opened.getResponse().getContentAsString(), "$.id");
    }

    private MvcTestResult post(String uri, String json) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }
}
