package com.queueless.canteen.model;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** ORDER_ITEMS table row (itemName is joined in from MENU_ITEMS for display). */
public record OrderItem(int orderItemId, int orderId, int itemId, String itemName,
                        int quantity, BigDecimal itemPrice, BigDecimal subtotal) {
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("itemId", itemId);
        m.put("name", itemName);
        m.put("quantity", quantity);
        m.put("price", itemPrice);
        m.put("subtotal", subtotal);
        return m;
    }
}
