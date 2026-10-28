package dev.lucaswotta.tickethit.simulator.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/** Campos ausentes usam os padrões de simulator.defaults. */
record RoomRequest(
        @Pattern(regexp = "ATOMIC|NAIVE") String inventoryStrategy,
        @Min(1) @Max(5_000) Integer tickets,
        @Min(0) Integer bots) {}
