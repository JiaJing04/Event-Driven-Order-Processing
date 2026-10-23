package com.example.saga.inventory;

import com.example.saga.inventory.domain.StockItem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockItemTest {

    @Test
    void reserveAndReleaseAdjustAvailableQuantity() {
        StockItem item = new StockItem("SKU", 10);
        item.reserve(4);
        assertThat(item.getAvailableQuantity()).isEqualTo(6);
        item.release(4);
        assertThat(item.getAvailableQuantity()).isEqualTo(10);
    }

    @Test
    void cannotReserveMoreThanAvailable() {
        StockItem item = new StockItem("SKU", 3);
        assertThatThrownBy(() -> item.reserve(4)).isInstanceOf(IllegalStateException.class);
        assertThat(item.getAvailableQuantity()).isEqualTo(3);
    }
}
