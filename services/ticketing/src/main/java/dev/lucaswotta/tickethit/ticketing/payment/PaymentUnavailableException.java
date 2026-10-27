package dev.lucaswotta.tickethit.ticketing.payment;

public class PaymentUnavailableException extends RuntimeException {

    PaymentUnavailableException(Throwable cause) {
        super("Serviço de pagamento indisponível após as tentativas", cause);
    }
}
