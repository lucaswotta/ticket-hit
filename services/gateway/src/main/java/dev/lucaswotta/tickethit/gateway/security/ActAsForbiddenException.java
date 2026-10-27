package dev.lucaswotta.tickethit.gateway.security;

class ActAsForbiddenException extends RuntimeException {

    ActAsForbiddenException(String reason) {
        super(reason);
    }
}
