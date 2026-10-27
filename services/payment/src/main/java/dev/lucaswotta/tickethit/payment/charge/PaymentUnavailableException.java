package dev.lucaswotta.tickethit.payment.charge;

public class PaymentUnavailableException extends RuntimeException {

    PaymentUnavailableException() {
        super("Gateway de pagamento indisponível");
    }
}
