package dev.lucaswotta.tickethit.ticketing.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// O limite de quantidade é regra de negócio e fica no HoldService (422);
// aqui só se confere o formato (400).
record HoldRequest(@NotBlank String customerId, @NotNull Integer quantity) {}
