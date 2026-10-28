package dev.lucaswotta.tickethit.simulator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@Configuration(proxyBeanMethods = false)
class OAuth2ClientConfig {

    // A versão "service" funciona fora de uma requisição web, que é onde os bots
    // rodam. Ela guarda o token e pede outro ao Keycloak quando ele vence.
    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository registrations, OAuth2AuthorizedClientService clients) {
        return new SingleFlightAuthorizedClientManager(
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clients));
    }
}
