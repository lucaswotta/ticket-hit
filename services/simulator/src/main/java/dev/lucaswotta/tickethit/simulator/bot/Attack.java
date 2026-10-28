package dev.lucaswotta.tickethit.simulator.bot;

public enum Attack {
    /** Passe com assinatura inventada. */
    FORGED_PASS,
    /** Passe verdadeiro de outro bot. */
    STOLEN_PASS,
    /** Hold sem passar pela fila. */
    NO_PASS,
    /** Rajada de consultas acima do rate limit. */
    FLOOD
}
