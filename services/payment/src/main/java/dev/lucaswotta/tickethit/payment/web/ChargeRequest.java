package dev.lucaswotta.tickethit.payment.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

record ChargeRequest(@NotNull UUID holdId, @NotBlank String customerId) {}
