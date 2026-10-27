package dev.lucaswotta.tickethit.payment.charge;

import dev.lucaswotta.tickethit.payment.PaymentProperties;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/**
 * Comportamento do gateway falso. As taxas mudam em tempo de execução para o
 * botão de caos da demo e para os testes, sem reiniciar o serviço.
 */
@Component
public class FakeGateway {

    private final Duration decisionDelay;
    private volatile double declineRate;
    private volatile double unavailableRate;

    FakeGateway(PaymentProperties properties) {
        this.decisionDelay = properties.fake().decisionDelay();
        this.declineRate = properties.fake().declineRate();
        this.unavailableRate = properties.fake().unavailableRate();
    }

    public void declineRate(double rate) {
        this.declineRate = rate;
    }

    public void unavailableRate(double rate) {
        this.unavailableRate = rate;
    }

    Duration decisionDelay() {
        return decisionDelay;
    }

    boolean approves() {
        return ThreadLocalRandom.current().nextDouble() >= declineRate;
    }

    public void rejectIfUnavailable() {
        if (ThreadLocalRandom.current().nextDouble() < unavailableRate) {
            throw new PaymentUnavailableException();
        }
    }
}
