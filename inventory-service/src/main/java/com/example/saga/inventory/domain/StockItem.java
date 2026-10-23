package com.example.saga.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "stock_items")
public class StockItem {

    @Id
    @Column(name = "sku")
    private String sku;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    protected StockItem() {
    }

    public StockItem(String sku, int availableQuantity) {
        this.sku = sku;
        this.availableQuantity = availableQuantity;
    }

    public String getSku() {
        return sku;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(int availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    public void reserve(int quantity) {
        if (quantity > availableQuantity) {
            throw new IllegalStateException("Cannot reserve " + quantity + " of " + sku + ", only " + availableQuantity + " available");
        }
        availableQuantity -= quantity;
    }

    public void release(int quantity) {
        availableQuantity += quantity;
    }
}
