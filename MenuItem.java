package com.queueless.canteen.model;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** MENU_ITEMS table row. availabilityStatus is AVAILABLE or UNAVAILABLE. */
public record MenuItem(int itemId, String itemName, String category, BigDecimal price, String availabilityStatus) {
    public boolean isAvailable() {
        return "AVAILABLE".equals(availabilityStatus);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", itemId);
        m.put("name", itemName);
        m.put("category", category);
        m.put("price", price);
        m.put("status", availabilityStatus);
        return m;
    }
}
