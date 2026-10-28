package dev.lucaswotta.tickethit.ticketing.hold;

import dev.lucaswotta.tickethit.ticketing.payment.PaymentClient;
import dev.lucaswotta.tickethit.ticketing.payment.PaymentUnavailableException;
import dev.lucaswotta.tickethit.ticketing.sale.InventoryStrategy;
import dev.lucaswotta.tickethit.ticketing.sale.Sale;
import dev.lucaswotta.tickethit.ticketing.sale.SaleNotFoundException;
import dev.lucaswotta.tickethit.ticketing.sale.SaleRepository;
import dev.lucaswotta.tickethit.ticketing.sale.TicketInventory;
import dev.lucaswotta.tickethit.ticketing.telemetry.Telemetry;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HoldService {

    private static final Logger log = LoggerFactory.getLogger(HoldService.class);

    private final SaleRepository sales;
    private final HoldRepository holds;
    private final Map<InventoryStrategy, TicketInventory> inventories;
    private final HoldProperties properties;
    private final PaymentClient paymentClient;
    private final Telemetry telemetry;
    private final Clock clock;

    HoldService(
            SaleRepository sales,
            HoldRepository holds,
            List<TicketInventory> inventories,
            HoldProperties properties,
            PaymentClient paymentClient,
            Telemetry telemetry,
            Clock clock) {
        this.sales = sales;
        this.holds = holds;
        this.inventories =
                inventories.stream().collect(Collectors.toMap(TicketInventory::strategy, Function.identity()));
        this.properties = properties;
        this.paymentClient = paymentClient;
        this.telemetry = telemetry;
        this.clock = clock;
    }

    // Retirada do estoque e criação do hold na mesma transação: se o INSERT
    // falhar, os ingressos voltam ao estoque no rollback.
    @Transactional
    public Hold hold(UUID saleId, String customerId, int quantity, UUID passId) {
        if (quantity < 1 || quantity > properties.maxQuantity()) {
            throw new InvalidQuantityException(quantity, properties.maxQuantity());
        }
        Sale sale = sales.find(saleId).orElseThrow(() -> new SaleNotFoundException(saleId));
        if (holds.passUsed(saleId, passId)) {
            throw new PassAlreadyUsedException(saleId);
        }
        OptionalInt remaining = inventories.get(sale.inventoryStrategy()).tryWithdraw(saleId, quantity);
        if (remaining.isEmpty()) {
            throw new SoldOutException(saleId, quantity);
        }
        Hold hold;
        try {
            hold = holds.create(saleId, customerId, quantity, passId, clock.instant().plus(properties.duration()));
        } catch (DuplicateKeyException concurrentUseOfThePass) {
            throw new PassAlreadyUsedException(saleId);
        }
        telemetry.publishAfterCommit(saleId, "HOLD_CREATED", customerId,
                Map.of("holdId", hold.id().toString(), "quantity", quantity, "available", remaining.getAsInt()));
        return hold;
    }

    public Hold find(UUID holdId) {
        return holds.find(holdId).orElseThrow(() -> new HoldNotFoundException(holdId));
    }

    // O hold de outro comprador responde como inexistente: um 403 confirmaria
    // que aquele id existe.
    public Hold findOwned(UUID holdId, String customerId) {
        Hold hold = find(holdId);
        if (!hold.customerId().equals(customerId)) {
            throw new HoldNotFoundException(holdId);
        }
        return hold;
    }

    /**
     * Sem transação de propósito: segurar uma conexão do banco aberta durante
     * uma chamada HTTP com retry esgotaria o pool sob carga. Cada passo grava
     * sozinho, e a falha desfaz o PAYING explicitamente.
     */
    public Hold pay(UUID holdId, String customerId) {
        findOwned(holdId, customerId);
        Hold paying = holds.startPayment(holdId, clock.instant())
                .orElseThrow(() -> new HoldNotPayableException(holdId, find(holdId).status()));
        try {
            paymentClient.charge(paying.id(), paying.customerId());
        } catch (PaymentUnavailableException unavailable) {
            holds.revertPayment(holdId);
            telemetry.publish(paying.saleId(), "PAYMENT_UNAVAILABLE", customerId, Map.of("holdId", holdId.toString()));
            throw unavailable;
        }
        telemetry.publish(paying.saleId(), "PAYMENT_REQUESTED", customerId, Map.of("holdId", holdId.toString()));
        return paying;
    }

    @Transactional
    public void confirm(UUID holdId) {
        var confirmed = holds.confirm(holdId, clock.instant());
        confirmed.ifPresent(hold -> telemetry.publishAfterCommit(hold.saleId(), "HOLD_CONFIRMED", hold.customerId(),
                Map.of("holdId", hold.id().toString(), "ticket", hold.lastTicketNumber())));
        if (confirmed.isEmpty()) {
            HoldStatus status = find(holdId).status();
            if (status != HoldStatus.CONFIRMED) {
                log.warn("Pagamento aprovado para o hold {} em {}: ingresso não emitido, estorno necessário", holdId, status);
            }
        }
    }

    @Transactional
    public void release(UUID holdId) {
        holds.release(holdId).ifPresent(released -> telemetry.publishAfterCommit(released.saleId(), "HOLD_RELEASED",
                released.customerId(), Map.of("holdId", holdId.toString(), "available", released.availableTickets())));
    }

    @Transactional
    public int expireDue() {
        return holds.expireDue(clock.instant(), properties.expirationBatchSize());
    }
}
