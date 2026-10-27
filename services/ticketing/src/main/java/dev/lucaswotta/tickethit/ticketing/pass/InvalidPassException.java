package dev.lucaswotta.tickethit.ticketing.pass;

public class InvalidPassException extends RuntimeException {

    InvalidPassException(String reason) {
        super("Passe de acesso inválido: " + reason);
    }

    InvalidPassException(String reason, Throwable cause) {
        super("Passe de acesso inválido: " + reason, cause);
    }
}
