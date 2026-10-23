package com.example.saga.inventory.api;

import com.example.saga.inventory.domain.Reservation;
import com.example.saga.inventory.domain.ReservationRepository;
import com.example.saga.inventory.domain.ReservationStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    public record ReservationResponse(UUID orderId, ReservationStatus status, List<Line> items) {
        public record Line(String sku, int quantity) {
        }

        static ReservationResponse from(Reservation r) {
            return new ReservationResponse(r.getOrderId(), r.getStatus(),
                    r.getItems().stream().map(l -> new Line(l.getSku(), l.getQuantity())).toList());
        }
    }

    private final ReservationRepository reservations;

    public ReservationController(ReservationRepository reservations) {
        this.reservations = reservations;
    }

    @GetMapping("/{orderId}")
    @Transactional(readOnly = true)
    public ResponseEntity<ReservationResponse> get(@PathVariable UUID orderId) {
        return reservations.findById(orderId)
                .map(r -> ResponseEntity.ok(ReservationResponse.from(r)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
