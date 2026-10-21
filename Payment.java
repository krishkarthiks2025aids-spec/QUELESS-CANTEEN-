package com.queueless.canteen.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** PAYMENTS table row. method: CASH/UPI/CARD. status: PENDING/SUCCESS/FAILED. */
public record Payment(int paymentId, int orderId, String paymentMethod, BigDecimal paymentAmount,
                      String paymentStatus, LocalDateTime paymentTime) {
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", paymentId);
        m.put("method", paymentMethod);
        m.put("amount", paymentAmount);
        m.put("status", paymentStatus);
        m.put("time", paymentTime);
        return m;
    }
}
