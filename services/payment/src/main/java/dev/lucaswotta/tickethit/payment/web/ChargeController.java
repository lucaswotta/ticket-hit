package dev.lucaswotta.tickethit.payment.web;

import dev.lucaswotta.tickethit.payment.charge.ChargeResult;
import dev.lucaswotta.tickethit.payment.charge.ChargeService;
import dev.lucaswotta.tickethit.payment.charge.FakeGateway;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ChargeController {

    private final ChargeService chargeService;
    private final FakeGateway gateway;

    ChargeController(ChargeService chargeService, FakeGateway gateway) {
        this.chargeService = chargeService;
        this.gateway = gateway;
    }

    // 202: a cobrança é aceita e decidida depois; o resultado sai em payment.events.
    @PostMapping("/api/charges")
    ResponseEntity<ChargeResponse> charge(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String idempotencyKey,
            @Valid @RequestBody ChargeRequest request) {
        gateway.rejectIfUnavailable();
        ChargeResult result = chargeService.charge(idempotencyKey, request.holdId(), request.customerId());
        return ResponseEntity.accepted()
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(ChargeResponse.of(result.charge()));
    }

    @GetMapping("/api/charges/{chargeId}")
    ChargeResponse find(@PathVariable UUID chargeId) {
        return ChargeResponse.of(chargeService.find(chargeId));
    }
}
