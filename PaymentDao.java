package com.queueless.canteen.dao;

import com.queueless.canteen.model.Payment;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/** JDBC access for the PAYMENTS table (one payment row per order). */
public class PaymentDao {

    public void insertPending(Connection c, int orderId, String method, BigDecimal amount) throws SQLException {
        String sql = "INSERT INTO payments(order_id, payment_method, payment_amount, payment_status, payment_time) "
                + "VALUES(?,?,?,'PENDING',NULL)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setString(2, method);
            ps.setBigDecimal(3, amount);
            ps.executeUpdate();
        }
    }

    public Payment findByOrder(Connection c, int orderId) throws SQLException {
        String sql = "SELECT payment_id, order_id, payment_method, payment_amount, payment_status, payment_time "
                + "FROM payments WHERE order_id = ? ORDER BY payment_id DESC LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Timestamp t = rs.getTimestamp("payment_time");
                return new Payment(rs.getInt("payment_id"), rs.getInt("order_id"), rs.getString("payment_method"),
                        rs.getBigDecimal("payment_amount"), rs.getString("payment_status"),
                        t == null ? null : t.toLocalDateTime());
            }
        }
    }

    /** method may be null to keep the existing one; time is null while the payment is still PENDING. */
    public void update(Connection c, int orderId, String status, String method, LocalDateTime time) throws SQLException {
        String sql = "UPDATE payments SET payment_status = ?, payment_time = ?, "
                + "payment_method = COALESCE(?, payment_method) WHERE order_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setTimestamp(2, time == null ? null : Timestamp.valueOf(time));
            ps.setString(3, method);
            ps.setInt(4, orderId);
            ps.executeUpdate();
        }
    }

    /** Sum of successful payments made on/after the given moment. */
    public BigDecimal revenueSince(Connection c, LocalDateTime since) throws SQLException {
        String sql = "SELECT COALESCE(SUM(payment_amount), 0) FROM payments WHERE payment_status = 'SUCCESS' AND payment_time >= ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(since));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }
}
