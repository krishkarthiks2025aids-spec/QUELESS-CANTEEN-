package com.queueless.canteen.dao;

import com.queueless.canteen.model.Student;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** JDBC access for the STUDENTS table. */
public class StudentDao {

    public Student insert(Connection c, String name, String email, String contact) throws SQLException {
        String sql = "INSERT INTO students(student_name, email, contact_info) VALUES(?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, contact);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return new Student(rs.getInt(1), name, email, contact);
            }
        }
    }

    public Student findByEmail(Connection c, String email) throws SQLException {
        String sql = "SELECT student_id, student_name, email, contact_info FROM students WHERE LOWER(email) = LOWER(?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public Student findById(Connection c, int id) throws SQLException {
        String sql = "SELECT student_id, student_name, email, contact_info FROM students WHERE student_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public List<Student> findAll(Connection c) throws SQLException {
        List<Student> list = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT student_id, student_name, email, contact_info FROM students ORDER BY student_id DESC")) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public int count(Connection c) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM students")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private Student map(ResultSet rs) throws SQLException {
        return new Student(rs.getInt("student_id"), rs.getString("student_name"),
                rs.getString("email"), rs.getString("contact_info"));
    }
}
