package dev.lucaswotta.tickethit.ticketing.sale;

import java.util.UUID;

public class SaleNotFoundException extends RuntimeException {

    public SaleNotFoundException(UUID saleId) {
        super("Venda %s não encontrada".formatted(saleId));
    }
}
