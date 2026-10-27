package dev.lucaswotta.tickethit.ticketing.hold;

import dev.lucaswotta.tickethit.ticketing.payment.PaymentClient;
import dev.lucaswotta.tickethit.ticketing.payment.PaymentUnavailableException;
import dev.lucaswotta.tickethit.ticketing.sale.InventoryStrategy;
import dev.lucaswotta.tickethit.ticketing.sale.Sale;
import dev.lucaswotta.tickethit.ticketing.sale.SaleNotFoundException;
import dev.lucaswotta.tickethit.ticketing.sale.SaleRepository;
import dev.lucaswotta.tickethit.ticketing.sale.TicketInventory;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final Clock clock;

    HoldService(
            SaleRepository sales,
            HoldRepository holds,
            List<TicketInventory> inventories,
            HoldProperties properties,
            PaymentClient paymentClient,
            Clock clock) {
        this.sales = sales;
        this.holds = holds;
        this.inventories =
                inventories.stream().collect(Collectors.toMap(TicketInventory::strategy, Function.identity()));
        this.properties = properties;
        this.paymentClient = paymentClient;
        this.clock = clock;
    }

    // Retirada do estoque e criação do hold na mesma transação: se o INSERT
    // falhar, os ingressos voltam ao estoque no rollback.
    @Transactional
    public Hold hold(UUID saleId, String customerId, int quantity) {
        if (quantity < 1 || quantity > properties.maxQuantity()) {
            throw new InvalidQuantityException(quantity, properties.maxQuantity());
        }
        Sale sale = sales.find(saleId).orElseThrow(() -> new SaleNotFoundException(saleId));
        if (!inventories.get(sale.inventoryStrategy()).tryWithdraw(saleId, quantity)) {
            throw new SoldOutException(saleId, quantity);
        }
        return holds.create(saleId, customerId, quantity, clock.instant().plus(properties.duration()));
    }

    public Hold find(UUID holdId) {
        return holds.find(holdId).orElseThrow(() -> new HoldNotFoundException(holdId));
    }

    /**
     * Sem transação de propósito: segurar uma conexão do banco aberta durante
     * uma chamada HTTP com retry esgotaria o pool sob carga. Cada passo grava
     * sozinho, e a falha desfaz o PAYING explicitamente.
     */
    public Hold pay(UUID holdId) {
        Hold paying = holds.startPayment(holdId, clock.instant())
                .orElseThrow(() -> new HoldNotPayableException(holdId, find(holdId).status()));
        try {
            paymentClient.charge(paying.id(), paying.customerId());
        } catch (PaymentUnavailableException unavailable) {
            holds.revertPayment(holdId);
            throw unavailable;
        }
        return paying;
    }

    @Transactional
    public void confirm(UUID holdId) {
        if (holds.confirm(holdId, clock.instant()).isEmpty()) {
            HoldStatus status = find(holdId).status();
            if (status != HoldStatus.CONFIRMED) {
                log.warn("Pagamento aprovado para o hold {} em {}: ingresso não emitido, estorno necessário", holdId, status);
            }
        }
    }

    @Transactional
    public void release(UUID holdId) {
        holds.release(holdId);
    }

    @Transactional
    public int expireDue() {
        return holds.expireDue(clock.instant(), properties.expirationBatchSize());
    }
}
