package dev.lucaswotta.tickethit.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    // API sem sessão nem cookie, autenticada por bearer token: não há CSRF a proteger.
    @Bean
    SecurityWebFilterChain api(ServerHttpSecurity http) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(HttpMethod.GET, "/api/sales/*", "/api/queues/*").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/sales", "/api/queues").hasRole("simulator")
                        .pathMatchers("/api/**").authenticated()
                        .anyExchange().denyAll())
                .oauth2ResourceServer(server -> server.jwt(jwt -> jwt.jwtAuthenticationConverter(roles())))
                .build();
    }

    private static ReactiveJwtAuthenticationConverterAdapter roles() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRoles());
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}
