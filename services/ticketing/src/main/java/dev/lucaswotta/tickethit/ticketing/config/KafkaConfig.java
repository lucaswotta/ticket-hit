package dev.lucaswotta.tickethit.ticketing.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

@Configuration(proxyBeanMethods = false)
class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    // Falha temporária (banco fora, por exemplo) tenta 5 vezes, com 1 s entre
    // elas. JSON inválido nunca vai passar, então segue direto para o DLT
    // (payment-events-dlt, nome padrão do recoverer, mesma partição da origem).
    // Sem isso, a mensagem seria descartada depois das tentativas padrão.
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
        var deadLetter = new DeadLetterPublishingRecoverer(template);
        ConsumerRecordRecoverer recoverer = (record, exception) -> {
            log.warn(
                    "Mensagem de {}-{} offset {} enviada ao DLT: {}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    exception.getMessage());
            deadLetter.accept(record, exception);
        };
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1_000, 5));
        handler.addNotRetryableExceptions(JacksonException.class);
        return handler;
    }
}
