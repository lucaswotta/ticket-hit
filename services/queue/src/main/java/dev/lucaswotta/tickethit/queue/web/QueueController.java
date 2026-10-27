package dev.lucaswotta.tickethit.queue.web;

import dev.lucaswotta.tickethit.queue.room.Entry;
import dev.lucaswotta.tickethit.queue.room.QueueService;
import dev.lucaswotta.tickethit.queue.room.QueueStats;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
class QueueController {

    private static final String CUSTOMER_ID = "X-Customer-Id";

    private final QueueService queueService;

    QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @PostMapping("/api/queues")
    ResponseEntity<QueueStats> create(@Valid @RequestBody CreateQueueRequest request, UriComponentsBuilder uri) {
        queueService.create(request.saleId(), request.opensAt(), request.admissionsPerSecond());
        return ResponseEntity.created(uri.path("/api/queues/{id}").build(request.saleId()))
                .body(queueService.stats(request.saleId()));
    }

    @GetMapping("/api/queues/{saleId}")
    QueueStats stats(@PathVariable UUID saleId) {
        return queueService.stats(saleId);
    }

    // 201 na primeira entrada, 200 quando o comprador já estava: entrar de novo
    // não muda o lugar. O comprador vem do gateway, que o tira do token.
    @PostMapping("/api/queues/{saleId}/entries")
    ResponseEntity<Entry> join(@PathVariable UUID saleId, @RequestHeader(CUSTOMER_ID) String customerId) {
        boolean joined = queueService.join(saleId, customerId);
        return ResponseEntity.status(joined ? HttpStatus.CREATED : HttpStatus.OK)
                .body(queueService.entry(saleId, customerId));
    }

    // Só a própria entrada: com o id na URL, qualquer um leria o passe de outro.
    @GetMapping("/api/queues/{saleId}/entries/me")
    Entry entry(@PathVariable UUID saleId, @RequestHeader(CUSTOMER_ID) String customerId) {
        return queueService.entry(saleId, customerId);
    }
}
