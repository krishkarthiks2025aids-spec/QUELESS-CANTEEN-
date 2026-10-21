package com.queueless.canteen.util;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Tiny JSON writer (Maps, Lists, Strings, numbers, booleans, LocalDateTime) - no external library needed. */
public final class Json {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Json() { }

    public static String write(Object o) {
        StringBuilder sb = new StringBuilder();
        append(sb, o);
        return sb.toString();
    }

    private static void append(StringBuilder sb, Object o) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof String s) {
            quote(sb, s);
        } else if (o instanceof Boolean || o instanceof Integer || o instanceof Long) {
            sb.append(o);
        } else if (o instanceof BigDecimal bd) {
            sb.append(bd.toPlainString());
        } else if (o instanceof Number n) {
            sb.append(n);
        } else if (o instanceof LocalDateTime t) {
            quote(sb, FMT.format(t));
        } else if (o instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                quote(sb, String.valueOf(e.getKey()));
                sb.append(':');
                append(sb, e.getValue());
            }
            sb.append('}');
        } else if (o instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object x : it) {
                if (!first) sb.append(',');
                first = false;
                append(sb, x);
            }
            sb.append(']');
        } else {
            quote(sb, o.toString());
        }
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) sb.append(String.format("\\u%04x", (int) ch));
                    else sb.append(ch);
                }
            }
        }
        sb.append('"');
    }
}
