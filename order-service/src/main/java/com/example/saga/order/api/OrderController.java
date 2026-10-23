package com.example.saga.order.api;

import com.example.saga.order.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    /**
     * Creates an order. The {@code Idempotency-Key} header is mandatory: retrying with the same key and body
     * returns the original order ({@code 200} + {@code Idempotent-Replay: true}) instead of creating another.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 200) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        OrderService.CreateResult result = service.create(idempotencyKey, request);
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/orders/" + result.order().id())).body(result.order());
        }
        return ResponseEntity.ok().header("Idempotent-Replay", "true").body(result.order());
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return service.get(id);
    }
}
