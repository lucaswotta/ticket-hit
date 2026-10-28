package dev.lucaswotta.tickethit.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

// A porta 1 não tem ninguém escutando: a conexão é recusada na hora.
@SpringBootTest(
        properties = {
            "QUEUE_URI=http://127.0.0.1:1",
            "TICKETING_URI=http://127.0.0.1:1",
            "SIMULATOR_URI=http://127.0.0.1:1",
            "XRAY_URI=http://127.0.0.1:1"
        })
@AutoConfigureWebTestClient
@Import(TestRedis.class)
class UpstreamDownIT {

    @Autowired
    WebTestClient client;

    @Test
    void unreachableServiceIsServiceUnavailable() {
        client.get()
                .uri("/api/sales/x")
                .exchange()
                .expectStatus()
                .isEqualTo(503)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("UPSTREAM_UNAVAILABLE");
    }
}
