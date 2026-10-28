package dev.lucaswotta.tickethit.simulator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * O starter de OAuth2 client está aqui para o simulator obter o próprio token,
 * não para proteger as rotas dele. Quem autentica o visitante é o gateway, como
 * nos outros serviços; sem esta configuração, o Spring ligaria login por formulário.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http) {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .build();
    }
}
