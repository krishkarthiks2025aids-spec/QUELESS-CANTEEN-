package com.queueless.canteen.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** JDBC connection factory for the queueless_canteen MySQL database. */
public final class Db {
    private static final String URL = Config.get("db.url", "DB_URL",
            "jdbc:mysql://localhost:3306/queueless_canteen?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true");
    private static final String USER = Config.get("db.user", "DB_USER", "root");
    private static final String PASSWORD = Config.get("db.password", "DB_PASSWORD", "");

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("MySQL Connector/J is missing - check the pom.xml dependency", e);
        }
    }

    private Db() { }

    public static Connection get() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static String describe() {
        return URL.replaceAll("\\?.*$", "") + " as user '" + USER + "'";
    }
}
