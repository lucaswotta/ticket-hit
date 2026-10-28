package dev.lucaswotta.tickethit.simulator.bot;

public enum Outcome {
    CONFIRMED,
    DECLINED,
    SOLD_OUT,
    PASS_EXPIRED,
    GAVE_UP,
    ERROR,
    BLOCKED_FORGED_PASS,
    BLOCKED_STOLEN_PASS,
    BLOCKED_NO_PASS,
    BLOCKED_FLOOD,
    /** Um ataque passou. Numa sala saudável, este contador fica em zero. */
    ATTACK_SUCCEEDED
}
