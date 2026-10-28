package dev.lucaswotta.tickethit.ticketing.web;

import dev.lucaswotta.tickethit.ticketing.hold.Hold;
import dev.lucaswotta.tickethit.ticketing.hold.HoldService;
import dev.lucaswotta.tickethit.ticketing.hold.InvalidQuantityException;
import dev.lucaswotta.tickethit.ticketing.hold.PassAlreadyUsedException;
import dev.lucaswotta.tickethit.ticketing.hold.SoldOutException;
import dev.lucaswotta.tickethit.ticketing.pass.InvalidPassException;
import dev.lucaswotta.tickethit.ticketing.pass.PassVerifier;
import dev.lucaswotta.tickethit.ticketing.sale.Sale;
import dev.lucaswotta.tickethit.ticketing.sale.SaleNotFoundException;
import dev.lucaswotta.tickethit.ticketing.sale.SaleRepository;
import dev.lucaswotta.tickethit.ticketing.telemetry.Telemetry;
import jakarta.validation.Valid;
import java.util.Map;
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

    // Posto pelo gateway a partir do token; o valor enviado pelo cliente é descartado lá.
    private static final String CUSTOMER_ID = "X-Customer-Id";

    private final SaleRepository sales;
    private final HoldService holdService;
    private final PassVerifier passVerifier;
    private final Telemetry telemetry;

    SaleController(SaleRepository sales, HoldService holdService, PassVerifier passVerifier, Telemetry telemetry) {
        this.sales = sales;
        this.holdService = holdService;
        this.passVerifier = passVerifier;
        this.telemetry = telemetry;
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
            @RequestHeader(CUSTOMER_ID) String customerId,
            @RequestHeader("Queue-Pass") String pass,
            @Valid @RequestBody HoldRequest request,
            UriComponentsBuilder uri) {
        Hold hold;
        try {
            UUID passId = passVerifier.verify(pass, saleId, customerId);
            hold = holdService.hold(saleId, customerId, request.quantity(), passId);
        } catch (InvalidPassException | PassAlreadyUsedException | SoldOutException | InvalidQuantityException rejected) {
            telemetry.publish(saleId, "HOLD_REJECTED", customerId, Map.of("code", rejectionCode(rejected)));
            throw rejected;
        }
        return ResponseEntity.created(uri.path("/api/holds/{id}").build(hold.id())).body(hold);
    }

    // 202: o pagamento foi aceito e a confirmação chega depois, pelo Kafka.
    @PostMapping("/api/holds/{holdId}/payment")
    ResponseEntity<Hold> pay(@PathVariable UUID holdId, @RequestHeader(CUSTOMER_ID) String customerId) {
        return ResponseEntity.accepted().body(holdService.pay(holdId, customerId));
    }

    // Os mesmos códigos que o ApiExceptionHandler devolve ao cliente.
    private static String rejectionCode(RuntimeException rejected) {
        return switch (rejected) {
            case InvalidPassException invalid -> "INVALID_PASS";
            case PassAlreadyUsedException used -> "PASS_ALREADY_USED";
            case SoldOutException soldOut -> "SOLD_OUT";
            default -> "INVALID_QUANTITY";
        };
    }

    @GetMapping("/api/holds/{holdId}")
    Hold findHold(@PathVariable UUID holdId, @RequestHeader(CUSTOMER_ID) String customerId) {
        return holdService.findOwned(holdId, customerId);
    }
}
