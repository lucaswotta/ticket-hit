package dev.lucaswotta.tickethit.ticketing.web;

import dev.lucaswotta.tickethit.ticketing.hold.Hold;
import dev.lucaswotta.tickethit.ticketing.hold.HoldService;
import dev.lucaswotta.tickethit.ticketing.pass.PassVerifier;
import dev.lucaswotta.tickethit.ticketing.sale.Sale;
import dev.lucaswotta.tickethit.ticketing.sale.SaleNotFoundException;
import dev.lucaswotta.tickethit.ticketing.sale.SaleRepository;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
class SaleController {

    private final SaleRepository sales;
    private final HoldService holdService;
    private final PassVerifier passVerifier;

    SaleController(SaleRepository sales, HoldService holdService, PassVerifier passVerifier) {
        this.sales = sales;
        this.holdService = holdService;
        this.passVerifier = passVerifier;
    }

    @PostMapping("/api/sales")
    ResponseEntity<Sale> open(@Valid @RequestBody OpenSaleRequest request, UriComponentsBuilder uri) {
        UUID saleId = sales.open(request.name(), request.totalTickets(), request.inventoryStrategyOrDefault());
        Sale sale = sales.find(saleId).orElseThrow();
        return ResponseEntity.created(uri.path("/api/sales/{id}").build(saleId)).body(sale);
    }

    @GetMapping("/api/sales/{saleId}")
    Sale find(@PathVariable UUID saleId) {
        return sales.find(saleId).orElseThrow(() -> new SaleNotFoundException(saleId));
    }

    // O passe vem da queue: sem ele não se segura ingresso, e ele só vale para
    // a venda e o comprador para os quais foi emitido.
    @PostMapping("/api/sales/{saleId}/holds")
    ResponseEntity<Hold> hold(
            @PathVariable UUID saleId,
            @RequestHeader("Queue-Pass") String pass,
            @Valid @RequestBody HoldRequest request,
            UriComponentsBuilder uri) {
        UUID passId = passVerifier.verify(pass, saleId, request.customerId());
        Hold hold = holdService.hold(saleId, request.customerId(), request.quantity(), passId);
        return ResponseEntity.created(uri.path("/api/holds/{id}").build(hold.id())).body(hold);
    }

    // 202: o pagamento foi aceito e a confirmação chega depois, pelo Kafka.
    @PostMapping("/api/holds/{holdId}/payment")
    ResponseEntity<Hold> pay(@PathVariable UUID holdId) {
        return ResponseEntity.accepted().body(holdService.pay(holdId));
    }

    @GetMapping("/api/holds/{holdId}")
    Hold findHold(@PathVariable UUID holdId) {
        return holdService.find(holdId);
    }
}
