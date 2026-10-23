package com.example.saga.inventory.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "reservation_items", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "line_no")
    private List<ReservationLine> items = new ArrayList<>();

    protected Reservation() {
    }

    public static Reservation reserved(UUID orderId, List<ReservationLine> lines) {
        Reservation r = new Reservation();
        r.orderId = orderId;
        r.status = ReservationStatus.RESERVED;
        r.items = new ArrayList<>(lines);
        r.createdAt = Instant.now();
        r.updatedAt = r.createdAt;
        return r;
    }

    /** @return true if it was RESERVED and is now RELEASED; false if already released */
    public boolean release() {
        if (status != ReservationStatus.RESERVED) {
            return false;
        }
        status = ReservationStatus.RELEASED;
        updatedAt = Instant.now();
        return true;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<ReservationLine> getItems() {
        return List.copyOf(items);
    }
}
