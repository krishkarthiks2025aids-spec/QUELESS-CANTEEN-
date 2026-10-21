package com.queueless.canteen.util;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Creates the tables (schema.sql) and seeds a starter menu the first time the app runs. */
public final class DbInit {
    private DbInit() { }

    public static void run() throws SQLException, IOException {
        try (Connection c = Db.get()) {
            runSchema(c);
            seedMenu(c);
        }
    }

    private static void runSchema(Connection c) throws SQLException, IOException {
        String sql;
        try (InputStream in = DbInit.class.getResourceAsStream("/schema.sql")) {
            if (in == null) throw new IOException("schema.sql not found on the classpath");
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        StringBuilder clean = new StringBuilder();
        for (String line : sql.split("\\r?\\n")) {
            if (!line.trim().startsWith("--")) clean.append(line).append('\n');
        }
        try (Statement st = c.createStatement()) {
            for (String stmt : clean.toString().split(";")) {
                if (!stmt.isBlank()) st.execute(stmt.trim());
            }
        }
    }

    private static void seedMenu(Connection c) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM menu_items")) {
            rs.next();
            if (rs.getInt(1) > 0) return;
        }
        Object[][] items = {
                {"Idli (3 pcs)", "Breakfast", "30.00"},
                {"Masala Dosa", "Breakfast", "50.00"},
                {"Pongal", "Breakfast", "40.00"},
                {"Veg Meals", "Meals", "70.00"},
                {"Chapati with Kurma", "Meals", "45.00"},
                {"Veg Fried Rice", "Meals", "60.00"},
                {"Curd Rice", "Meals", "35.00"},
                {"Samosa", "Snacks", "15.00"},
                {"Veg Puff", "Snacks", "20.00"},
                {"French Fries", "Snacks", "40.00"},
                {"Veg Sandwich", "Snacks", "35.00"},
                {"Tea", "Beverages", "10.00"},
                {"Filter Coffee", "Beverages", "15.00"},
                {"Cold Coffee", "Beverages", "40.00"},
                {"Lime Juice", "Beverages", "20.00"},
                {"Gulab Jamun (2 pcs)", "Desserts", "25.00"},
                {"Ice Cream Cup", "Desserts", "30.00"},
        };
        String sql = "INSERT INTO menu_items(item_name, category, price, availability_status) VALUES(?,?,?,'AVAILABLE')";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (Object[] it : items) {
                ps.setString(1, (String) it[0]);
                ps.setString(2, (String) it[1]);
                ps.setBigDecimal(3, new BigDecimal((String) it[2]));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
