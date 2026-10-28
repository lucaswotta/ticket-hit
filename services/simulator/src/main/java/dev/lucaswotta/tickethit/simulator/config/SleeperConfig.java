package dev.lucaswotta.tickethit.simulator.config;

import dev.lucaswotta.tickethit.simulator.bot.Sleeper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class SleeperConfig {

    @Bean
    Sleeper sleeper() {
        return Thread::sleep;
    }
}
