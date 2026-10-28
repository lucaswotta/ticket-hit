package dev.lucaswotta.tickethit.simulator.config;

import java.util.concurrent.locks.ReentrantLock;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

/**
 * Uma renovação de token por vez. Sem isso, quando o token vence, centenas de
 * bots percebem ao mesmo tempo e cada um pede um token novo ao Keycloak; parte
 * dos pedidos falha. Com a trava, o primeiro renova e os outros recebem o token
 * já guardado. Com o token válido, a chamada só lê o que está guardado.
 */
final class SingleFlightAuthorizedClientManager implements OAuth2AuthorizedClientManager {

    private final OAuth2AuthorizedClientManager delegate;
    private final ReentrantLock lock = new ReentrantLock();

    SingleFlightAuthorizedClientManager(OAuth2AuthorizedClientManager delegate) {
        this.delegate = delegate;
    }

    @Override
    public OAuth2AuthorizedClient authorize(OAuth2AuthorizeRequest request) {
        lock.lock();
        try {
            return delegate.authorize(request);
        } finally {
            lock.unlock();
        }
    }
}
