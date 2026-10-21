package com.queueless.canteen.dao;

import com.queueless.canteen.model.Order;
import com.queueless.canteen.model.OrderItem;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JDBC access for ORDERS and ORDER_ITEMS (payments are loaded through PaymentDao). */
public class OrderDao {
    private static final String SELECT =
            "SELECT o.order_id, o.student_id, s.student_name, s.contact_info, o.order_time, o.total_amount, "
                    + "o.token_number, o.order_status, o.estimated_wait_minutes "
                    + "FROM orders o JOIN students s ON s.student_id = o.student_id ";

    private final PaymentDao payments = new PaymentDao();

    // ------------------------------------------------------------------ writes

    /** Next token for the day: 101, 102, ... resets every day. */
    public int nextToken(Connection c, LocalDateTime dayStart) throws SQLException {
        String sql = "SELECT COALESCE(MAX(token_number), 100) + 1 FROM orders WHERE order_time >= ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(dayStart));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public int insert(Connection c, int studentId, LocalDateTime time, BigDecimal total, int token, int estWait) throws SQLException {
        String sql = "INSERT INTO orders(student_id, order_time, total_amount, token_number, order_status, estimated_wait_minutes) "
                + "VALUES(?,?,?,?,'PLACED',?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, studentId);
            ps.setTimestamp(2, Timestamp.valueOf(time));
            ps.setBigDecimal(3, total);
            ps.setInt(4, token);
            ps.setInt(5, estWait);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void insertItem(Connection c, int orderId, int itemId, int qty, BigDecimal price, BigDecimal subtotal) throws SQLException {
        String sql = "INSERT INTO order_items(order_id, item_id, quantity, item_price, subtotal) VALUES(?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setInt(2, itemId);
            ps.setInt(3, qty);
            ps.setBigDecimal(4, price);
            ps.setBigDecimal(5, subtotal);
            ps.executeUpdate();
        }
    }

    public void updateStatus(Connection c, int orderId, String status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE orders SET order_status = ? WHERE order_id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, orderId);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------ queue maths

    /** Orders still waiting to be made (PLACED or PREPARING). */
    public int countPending(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM orders WHERE order_status IN ('PLACED','PREPARING')")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Pending orders that were placed before this one. */
    public int countAhead(Connection c, int orderId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM orders WHERE order_status IN ('PLACED','PREPARING') AND order_id < ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public Map<String, Integer> countByStatusSince(Connection c, LocalDateTime since) throws SQLException {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (String s : new String[]{"PLACED", "PREPARING", "READY", "COMPLETED", "CANCELLED"}) m.put(s, 0);
        String sql = "SELECT order_status, COUNT(*) FROM orders WHERE order_time >= ? GROUP BY order_status";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(since));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) m.put(rs.getString(1), rs.getInt(2));
            }
        }
        return m;
    }

    // ------------------------------------------------------------------ reads

    public Order findById(Connection c, int id) throws SQLException {
        List<Order> list = query(c, SELECT + "WHERE o.order_id = ?", id);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<Order> findByStudent(Connection c, int studentId) throws SQLException {
        return query(c, SELECT + "WHERE o.student_id = ? ORDER BY o.order_id DESC LIMIT 30", studentId);
    }

    /** statuses == null/empty means all statuses. Newest 100 only. Active (queue) orders are oldest-first. */
    public List<Order> findByStatuses(Connection c, List<String> statuses, boolean oldestFirst) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT);
        if (statuses != null && !statuses.isEmpty()) {
            sql.append("WHERE o.order_status IN (");
            for (int i = 0; i < statuses.size(); i++) sql.append(i == 0 ? "?" : ",?");
            sql.append(") ");
        }
        sql.append(oldestFirst ? "ORDER BY o.order_id ASC LIMIT 100" : "ORDER BY o.order_id DESC LIMIT 100");
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            if (statuses != null) for (int i = 0; i < statuses.size(); i++) ps.setString(i + 1, statuses.get(i));
            return read(c, ps);
        }
    }

    private List<Order> query(Connection c, String sql, int param) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, param);
            return read(c, ps);
        }
    }

    private record Base(int id, int studentId, String name, String contact, LocalDateTime time,
                        BigDecimal total, int token, String status, int est) { }

    private List<Order> read(Connection c, PreparedStatement ps) throws SQLException {
        List<Base> bases = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                bases.add(new Base(rs.getInt("order_id"), rs.getInt("student_id"), rs.getString("student_name"),
                        rs.getString("contact_info"), rs.getTimestamp("order_time").toLocalDateTime(),
                        rs.getBigDecimal("total_amount"), rs.getInt("token_number"), rs.getString("order_status"),
                        rs.getInt("estimated_wait_minutes")));
            }
        }
        List<Order> orders = new ArrayList<>();
        for (Base b : bases) {
            orders.add(new Order(b.id(), b.studentId(), b.name(), b.contact(), b.time(), b.total(), b.token(),
                    b.status(), b.est(), loadItems(c, b.id()), payments.findByOrder(c, b.id())));
        }
        return orders;
    }

    private List<OrderItem> loadItems(Connection c, int orderId) throws SQLException {
        String sql = "SELECT oi.order_item_id, oi.order_id, oi.item_id, m.item_name, oi.quantity, oi.item_price, oi.subtotal "
                + "FROM order_items oi JOIN menu_items m ON m.item_id = oi.item_id WHERE oi.order_id = ? ORDER BY oi.order_item_id";
        List<OrderItem> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new OrderItem(rs.getInt("order_item_id"), rs.getInt("order_id"), rs.getInt("item_id"),
                            rs.getString("item_name"), rs.getInt("quantity"), rs.getBigDecimal("item_price"),
                            rs.getBigDecimal("subtotal")));
                }
            }
        }
        return list;
    }
}
