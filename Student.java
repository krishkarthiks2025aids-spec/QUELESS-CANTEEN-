package com.queueless.canteen.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** STUDENTS table row. */
public record Student(int studentId, String studentName, String email, String contactInfo) {
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", studentId);
        m.put("name", studentName);
        m.put("email", email);
        m.put("contact", contactInfo);
        return m;
    }
}
