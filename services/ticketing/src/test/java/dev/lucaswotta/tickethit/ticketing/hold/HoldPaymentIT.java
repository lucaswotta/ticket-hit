package dev.lucaswotta.tickethit.ticketing.hold;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import dev.lucaswotta.tickethit.ticketing.payment.PaymentClient;
import dev.lucaswotta.tickethit.ticketing.payment.PaymentUnavailableException;
import dev.lucaswotta.tickethit.ticketing.sale.InventoryStrategy;
import dev.lucaswotta.tickethit.ticketing.sale.SaleRepository;
import dev.lucaswotta.tickethit.ticketing.support.IntegrationTest;
import dev.lucaswotta.tickethit.ticketing.support.MutableClock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

@IntegrationTest
class HoldPaymentIT {

    @Autowired
    HoldService holdService;

    @Autowired
    SaleRepository sales;

    @Autowired
    PaymentClient paymentClient;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcClient jdbc;

    @AfterEach
    void resetPaymentClient() {
        reset(paymentClient);
    }

    @Test
    void payMovesHoldToPayingAndChargesWithTheHold() {
        Hold hold = holdService.hold(sales.open("Noite 1", 10, InventoryStrategy.ATOMIC), "ana", 2);

        Hold paying = holdService.pay(hold.id());

        assertThat(paying.status()).isEqualTo(HoldStatus.PAYING);
        assertThat(paying.paymentRequestedAt()).isNotNull();
        verify(paymentClient).charge(hold.id(), "ana");
    }

    @Test
    void unavailablePaymentRevertsHoldToHeld() {
        Hold hold = holdService.hold(sales.open("Noite 1", 10, InventoryStrategy.ATOMIC), "ana", 2);
        doThrow(mock(PaymentUnavailableException.class)).when(paymentClient).charge(eq(hold.id()), any());

        assertThatThrownBy(() -> holdService.pay(hold.id())).isInstanceOf(PaymentUnavailableException.class);

        assertThat(holdService.find(hold.id()).status()).isEqualTo(HoldStatus.HELD);
    }

    @Test
    void expiredHoldCannotBePaid() {
        Hold hold = holdService.hold(sales.open("Noite 1", 10, InventoryStrategy.ATOMIC), "ana", 2);
        clock.advance(Duration.ofMinutes(10));

        assertThatThrownBy(() -> holdService.pay(hold.id())).isInstanceOf(HoldNotPayableException.class);
    }

    @Test
    void payingHoldIsNotExpired() {
        UUID saleId = sales.open("Noite 1", 10, InventoryStrategy.ATOMIC);
        Hold hold = holdService.hold(saleId, "ana", 2);
        holdService.pay(hold.id());
        clock.advance(Duration.ofMinutes(10));

        expireAllDue();

        assertThat(holdService.find(hold.id()).status()).isEqualTo(HoldStatus.PAYING);
        assertThat(sales.availableTickets(saleId)).isEqualTo(8);
    }

    @Test
    void confirmNumbersThePurchaseAndRepeatedEventChangesNothing() {
        UUID saleId = sales.open("Noite 1", 10, InventoryStrategy.ATOMIC);
        Hold first = holdService.hold(saleId, "ana", 3);
        Hold second = holdService.hold(saleId, "bia", 2);

        holdService.confirm(first.id());
        holdService.confirm(second.id());
        holdService.confirm(second.id());

        assertThat(holdService.find(first.id()).lastTicketNumber()).isEqualTo(3);
        assertThat(holdService.find(second.id()).lastTicketNumber()).isEqualTo(5);
        assertThat(soldTickets(saleId)).isEqualTo(5);
    }

    @Test
    void releaseReturnsTicketsOnce() {
        UUID saleId = sales.open("Noite 1", 10, InventoryStrategy.ATOMIC);
        Hold hold = holdService.hold(saleId, "ana", 3);

        holdService.release(hold.id());
        holdService.release(hold.id());

        assertThat(holdService.find(hold.id()).status()).isEqualTo(HoldStatus.RELEASED);
        assertThat(sales.availableTickets(saleId)).isEqualTo(10);
    }

    @Test
    void lateApprovalForExpiredHoldIssuesNoTicket() {
        UUID saleId = sales.open("Noite 1", 10, InventoryStrategy.ATOMIC);
        Hold hold = holdService.hold(saleId, "ana", 3);
        clock.advance(Duration.ofMinutes(10));
        expireAllDue();

        holdService.confirm(hold.id());

        assertThat(holdService.find(hold.id()).status()).isEqualTo(HoldStatus.EXPIRED);
        assertThat(soldTickets(saleId)).isZero();
    }

    @Test
    void concurrentConfirmationsGetDistinctNumbers() throws Exception {
        UUID saleId = sales.open("Noite 1", 100, InventoryStrategy.ATOMIC);
        List<UUID> holdIds = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            holdIds.add(holdService.hold(saleId, "comprador-" + i, 1).id());
        }

        var start = new CountDownLatch(1);
        List<Future<?>> confirmations = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (UUID holdId : holdIds) {
                confirmations.add(executor.submit(() -> {
                    start.await();
                    holdService.confirm(holdId);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> confirmation : confirmations) {
                confirmation.get();
            }
        }

        List<Integer> numbers = holdIds.stream().map(id -> holdService.find(id).lastTicketNumber()).toList();
        assertThat(numbers).doesNotHaveDuplicates().allSatisfy(number -> assertThat(number).isBetween(1, 100));
    }

    private int soldTickets(UUID saleId) {
        return jdbc.sql("select sold_tickets from sale where id = :saleId")
                .param("saleId", saleId)
                .query(Integer.class)
                .single();
    }

    // Os testes dividem banco e relógio, então pode haver mais holds vencidos
    // que um lote do job; expira lote a lote até não sobrar nenhum.
    private void expireAllDue() {
        while (holdService.expireDue() > 0) {}
    }
}
