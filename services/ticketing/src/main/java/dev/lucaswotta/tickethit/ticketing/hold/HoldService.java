package dev.lucaswotta.tickethit.ticketing.hold;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HoldService {

    private final SaleRepository sales;
    private final HoldRepository holds;
    private final Map<InventoryStrategy, TicketInventory> inventories;
    private final HoldProperties properties;
    private final Clock clock;

    HoldService(
            SaleRepository sales,
            HoldRepository holds,
            List<TicketInventory> inventories,
            HoldProperties properties,
            Clock clock) {
        this.sales = sales;
        this.holds = holds;
        this.inventories =
                inventories.stream().collect(Collectors.toMap(TicketInventory::strategy, Function.identity()));
        this.properties = properties;
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

    @Transactional
    public int expireDue() {
        return holds.expireDue(clock.instant(), properties.expirationBatchSize());
    }
}
