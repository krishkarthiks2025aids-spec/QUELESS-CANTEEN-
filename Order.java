package com.queueless.canteen.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** ORDERS table row plus its items and payment. orderStatus: PLACED/PREPARING/READY/COMPLETED/CANCELLED. */
public record Order(int orderId, int studentId, String studentName, String studentContact,
                    LocalDateTime orderTime, BigDecimal totalAmount, int tokenNumber,
                    String orderStatus, int estimatedWaitMinutes,
                    List<OrderItem> items, Payment payment) {

    public int totalQuantity() {
        int q = 0;
        for (OrderItem i : items) q += i.quantity();
        return q;
    }

    public boolean isPending() {
        return "PLACED".equals(orderStatus) || "PREPARING".equals(orderStatus);
    }

    /** liveWaitMinutes is recomputed on every request from the current queue. */
    public Map<String, Object> toMap(int liveWaitMinutes) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", orderId);
        m.put("studentId", studentId);
        m.put("studentName", studentName);
        m.put("studentContact", studentContact);
        m.put("time", orderTime);
        m.put("total", totalAmount);
        m.put("token", tokenNumber);
        m.put("status", orderStatus);
        m.put("estimatedWaitAtOrder", estimatedWaitMinutes);
        m.put("estimatedWait", liveWaitMinutes);
        List<Object> its = new ArrayList<>();
        for (OrderItem i : items) its.add(i.toMap());
        m.put("items", its);
        m.put("payment", payment == null ? null : payment.toMap());
        return m;
    }
}
