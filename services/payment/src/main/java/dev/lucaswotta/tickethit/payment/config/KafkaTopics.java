package dev.lucaswotta.tickethit.payment.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration(proxyBeanMethods = false)
class KafkaTopics {

    // 3 partições permitem até 3 instâncias do ticketing consumindo em paralelo.
    @Bean
    NewTopic paymentEvents() {
        return TopicBuilder.name("payment.events").partitions(3).replicas(1).build();
    }
}
