package dev.lucaswotta.tickethit.ticketing.payment;

import java.net.http.HttpClient;
import java.util.UUID;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Cliente do payment. Timeout, falha de rede e 5xx são repetidos com backoff;
 * 4xx não, porque repetir o mesmo pedido inválido dá o mesmo erro. Todas as
 * tentativas levam a mesma Idempotency-Key, então um retry depois de uma
 * resposta perdida não gera segunda cobrança.
 */
@Component
public class PaymentClient {

    private final RestClient rest;
    private final RetryTemplate retry;

    public PaymentClient(PaymentClientProperties properties) {
        var http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        var requestFactory = new JdkClientHttpRequestFactory(http);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.rest = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
        this.retry = new RetryTemplate(RetryPolicy.builder()
                .maxRetries(properties.maxRetries())
                .delay(properties.retryDelay())
                .multiplier(2)
                .includes(ResourceAccessException.class, HttpServerErrorException.class)
                .build());
    }

    public void charge(UUID holdId, String customerId) {
        try {
            retry.execute(() -> rest.post()
                    .uri("/api/charges")
                    .header("Idempotency-Key", holdId.toString())
                    .body(new ChargeRequest(holdId, customerId))
                    .retrieve()
                    .toBodilessEntity());
        } catch (RetryException exhausted) {
            Throwable cause = exhausted.getCause();
            if (cause instanceof ResourceAccessException || cause instanceof HttpServerErrorException) {
                throw new PaymentUnavailableException(cause);
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(cause);
        }
    }

    private record ChargeRequest(UUID holdId, String customerId) {}
}
