package com.uteexpress.catalog.service;

import com.uteexpress.catalog.dto.ProductSnapshot;
import com.uteexpress.catalog.dto.CartProductSnapshot;
import java.util.List;
import java.util.Set;

/** HP-owned provider boundary agreed with TD; implementation remains in Catalog. */
public interface CatalogQueryService {
    /** Read current display data without rejecting unavailable products; missing IDs are omitted. */
    List<CartProductSnapshot> findCartProducts(Set<Long> productIds);

    /** All requested IDs must resolve and be purchasable; otherwise fail, never return partial prices. */
    List<ProductSnapshot> requirePurchasableProducts(Set<Long> productIds);
}
