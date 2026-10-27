package dev.lucaswotta.tickethit.ticketing.hold;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBooleanProperty(name = "ticketing.hold.expiration-enabled", matchIfMissing = true)
class HoldExpirationJob {

    private final HoldService holdService;

    HoldExpirationJob(HoldService holdService) {
        this.holdService = holdService;
    }

    @Scheduled(fixedDelayString = "${ticketing.hold.expiration-interval}")
    void expireDueHolds() {
        holdService.expireDue();
    }
}
