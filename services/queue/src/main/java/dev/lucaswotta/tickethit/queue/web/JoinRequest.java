package dev.lucaswotta.tickethit.queue.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record JoinRequest(@NotBlank @Size(max = 100) String customerId) {}
