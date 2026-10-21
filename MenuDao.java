package com.queueless.canteen.dao;

import com.queueless.canteen.model.MenuItem;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** JDBC access for the MENU_ITEMS table. */
public class MenuDao {
    private static final String COLS = "item_id, item_name, category, price, availability_status";

    /** Search by name/category text and/or an exact category. Both filters are optional. */
    public List<MenuItem> search(Connection c, String text, String category) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT " + COLS + " FROM menu_items WHERE 1=1");
        List<String> args = new ArrayList<>();
        if (text != null && !text.isBlank()) {
            sql.append(" AND (LOWER(item_name) LIKE ? OR LOWER(category) LIKE ?)");
            String like = "%" + text.trim().toLowerCase() + "%";
            args.add(like);
            args.add(like);
        }
        if (category != null && !category.isBlank()) {
            sql.append(" AND category = ?");
            args.add(category.trim());
        }
        sql.append(" ORDER BY category, item_name");
        List<MenuItem> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) ps.setString(i + 1, args.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    public MenuItem findById(Connection c, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT " + COLS + " FROM menu_items WHERE item_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public List<String> categories(Connection c) throws SQLException {
        List<String> list = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT category FROM menu_items ORDER BY category")) {
            while (rs.next()) list.add(rs.getString(1));
        }
        return list;
    }

    public int insert(Connection c, String name, String category, BigDecimal price, String status) throws SQLException {
        String sql = "INSERT INTO menu_items(item_name, category, price, availability_status) VALUES(?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, category);
            ps.setBigDecimal(3, price);
            ps.setString(4, status);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public boolean update(Connection c, int id, String name, String category, BigDecimal price, String status) throws SQLException {
        String sql = "UPDATE menu_items SET item_name = ?, category = ?, price = ?, availability_status = ? WHERE item_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, category);
            ps.setBigDecimal(3, price);
            ps.setString(4, status);
            ps.setInt(5, id);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(Connection c, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM menu_items WHERE item_id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public int count(Connection c) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM menu_items")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private MenuItem map(ResultSet rs) throws SQLException {
        return new MenuItem(rs.getInt("item_id"), rs.getString("item_name"), rs.getString("category"),
                rs.getBigDecimal("price"), rs.getString("availability_status"));
    }
}
