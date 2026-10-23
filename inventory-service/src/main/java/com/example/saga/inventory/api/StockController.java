package com.example.saga.inventory.api;

import com.example.saga.inventory.domain.StockItem;
import com.example.saga.inventory.domain.StockItemRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Small admin API to look at and seed stock. */
@RestController
@RequestMapping("/api/stock")
public class StockController {

    public record StockRequest(@Min(0) int availableQuantity) {
    }

    public record StockResponse(String sku, int availableQuantity) {
        static StockResponse from(StockItem item) {
            return new StockResponse(item.getSku(), item.getAvailableQuantity());
        }
    }

    private final StockItemRepository stock;

    public StockController(StockItemRepository stock) {
        this.stock = stock;
    }

    @GetMapping("/{sku}")
    public ResponseEntity<StockResponse> get(@PathVariable String sku) {
        return stock.findById(sku)
                .map(item -> ResponseEntity.ok(StockResponse.from(item)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Creates the SKU or overwrites its available quantity. */
    @PutMapping("/{sku}")
    @Transactional
    public StockResponse put(@PathVariable String sku, @Valid @RequestBody StockRequest request) {
        StockItem item = stock.findById(sku).orElseGet(() -> new StockItem(sku, request.availableQuantity()));
        item.setAvailableQuantity(request.availableQuantity());
        return StockResponse.from(stock.save(item));
    }
}
