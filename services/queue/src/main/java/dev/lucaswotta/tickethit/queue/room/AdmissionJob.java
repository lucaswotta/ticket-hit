package dev.lucaswotta.tickethit.queue.room;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBooleanProperty(name = "queue.admission-enabled", matchIfMissing = true)
class AdmissionJob {

    private final QueueService queueService;

    AdmissionJob(QueueService queueService) {
        this.queueService = queueService;
    }

    // O intervalo só define a granularidade; a taxa vem do token bucket em admit.lua.
    @Scheduled(fixedDelayString = "${queue.admission-interval}")
    void admitDue() {
        queueService.admitDue();
    }
}
