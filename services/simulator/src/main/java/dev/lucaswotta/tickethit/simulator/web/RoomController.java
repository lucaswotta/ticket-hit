package dev.lucaswotta.tickethit.simulator.web;

import dev.lucaswotta.tickethit.simulator.SimulatorProperties;
import dev.lucaswotta.tickethit.simulator.room.Room;
import dev.lucaswotta.tickethit.simulator.room.RoomService;
import dev.lucaswotta.tickethit.simulator.room.RoomSettings;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
class RoomController {

    // Posto pelo gateway a partir do token do visitante.
    private static final String CUSTOMER_ID = "X-Customer-Id";

    // Teto aceito pela queue em POST /api/queues.
    private static final int MAX_ADMISSIONS_PER_SECOND = 1_000;

    private final RoomService rooms;
    private final SimulatorProperties properties;

    RoomController(RoomService rooms, SimulatorProperties properties) {
        this.rooms = rooms;
        this.properties = properties;
    }

    // 202: a sala está aberta, mas os bots ainda vão chegar e comprar.
    @PostMapping("/api/rooms")
    ResponseEntity<RoomResponse> create(
            @RequestHeader(CUSTOMER_ID) String visitor,
            @Valid @RequestBody(required = false) RoomRequest request,
            UriComponentsBuilder uri) {
        RoomRequest asked = request == null ? new RoomRequest(null, null, null) : request;
        var defaults = properties.defaults();
        String strategy = Objects.requireNonNullElse(asked.inventoryStrategy(), "ATOMIC");
        // O modo sem proteção desliga as duas camadas. Com a fila liberando no
        // ritmo normal, poucos chegam juntos ao estoque e o overselling quase
        // não aparece: a fila sozinha já protege. Sem proteção, libera todos.
        int admissionsPerSecond = "NAIVE".equals(strategy) ? MAX_ADMISSIONS_PER_SECOND : defaults.admissionsPerSecond();
        var settings = new RoomSettings(
                strategy,
                Objects.requireNonNullElse(asked.tickets(), defaults.tickets()),
                Objects.requireNonNullElse(asked.bots(), defaults.bots()),
                admissionsPerSecond,
                defaults.lobby());
        Room room = rooms.create(visitor, settings);
        return ResponseEntity.accepted()
                .location(uri.path("/api/rooms/{id}").build(room.id()))
                .body(RoomResponse.of(room));
    }

    @GetMapping("/api/rooms/{roomId}")
    RoomResponse find(@PathVariable UUID roomId, @RequestHeader(CUSTOMER_ID) String visitor) {
        return RoomResponse.of(rooms.find(roomId, visitor));
    }
}
