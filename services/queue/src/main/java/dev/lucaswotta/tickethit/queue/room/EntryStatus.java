package dev.lucaswotta.tickethit.queue.room;

public enum EntryStatus {
    /** Entrou antes da abertura; a posição sai no sorteio. */
    WAITING_DRAW,
    WAITING,
    ADMITTED,
    PASS_EXPIRED
}
