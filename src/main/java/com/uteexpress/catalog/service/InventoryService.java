package com.uteexpress.catalog.service;

import java.util.List;
import com.uteexpress.catalog.dto.StockQuantity;

/** HP-owned boundary, no stock implementation. All methods join the caller's transaction. */
public interface InventoryService {
    /** Lock products in ascending ID order until transaction end and reject insufficient stock. */
    void lockAndCheck(List<StockQuantity> quantities);

    /** Require the same transaction and previously locked/checked quantities; roll back on any failure. */
    void decrease(List<StockQuantity> quantities);

    /** Caller locks order and guards inventory_released_at to ensure exactly-once release. */
    void restore(List<StockQuantity> quantities);
}
