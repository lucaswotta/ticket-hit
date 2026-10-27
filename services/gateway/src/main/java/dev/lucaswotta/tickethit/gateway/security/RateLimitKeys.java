package dev.lucaswotta.tickethit.gateway.security;

import java.net.InetSocketAddress;
import java.util.Optional;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
class RateLimitKeys {

    // Cada bot tem o próprio balde, mesmo com todos usando o token do simulator.
    // @Primary só escolhe o padrão do gateway; toda rota declara o seu resolver.
    @Bean
    @Primary
    KeyResolver customerKeyResolver(CustomerResolver customers) {
        return exchange -> customers.resolve(exchange)
                .map(customer -> "customer:" + customer)
                .onErrorResume(ActAsForbiddenException.class, forbidden -> Mono.empty());
    }

    // Sem endereço remoto, todas as requisições dividem um balde só: o limite
    // continua valendo em vez de cair no 403 de chave vazia do gateway.
    @Bean
    KeyResolver clientIpKeyResolver() {
        return exchange -> Mono.just(Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                .map(InetSocketAddress::getAddress)
                .map(address -> "ip:" + address.getHostAddress())
                .orElse("ip:unknown"));
    }
}
