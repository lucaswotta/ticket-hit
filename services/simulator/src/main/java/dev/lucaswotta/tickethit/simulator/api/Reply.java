package dev.lucaswotta.tickethit.simulator.api;

import tools.jackson.databind.JsonNode;

/** Status HTTP e corpo de uma resposta da API, para o bot decidir pelo status e pelo {@code code}. */
public record Reply(int status, JsonNode body) {

    public boolean is(int expected) {
        return status == expected;
    }

    public String text(String field) {
        JsonNode value = body == null ? null : body.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    public long number(String field) {
        JsonNode value = body == null ? null : body.get(field);
        return value == null || value.isNull() ? 0 : value.asLong();
    }

    public String code() {
        return text("code");
    }
}
