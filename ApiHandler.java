package com.queueless.canteen.web;

import com.queueless.canteen.service.ApiException;
import com.queueless.canteen.service.CanteenService;
import com.queueless.canteen.util.Config;
import com.queueless.canteen.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST-style JSON API under /api/*. Request data comes from the query string or a form-encoded body.
 * Staff-only calls must send the header X-Staff-Pin.
 */
public class ApiHandler implements HttpHandler {
    private static final String STAFF_PIN = Config.get("staff.pin", "STAFF_PIN", "1234");
    private final CanteenService svc = new CanteenService();

    // ------------------------------------------------------------------ request wrapper
    private static final class Req {
        final String method;
        final String[] seg;
        final Map<String, String> params = new LinkedHashMap<>();
        final String pin;

        Req(HttpExchange ex) throws IOException {
            method = ex.getRequestMethod().toUpperCase();
            String path = ex.getRequestURI().getRawPath();
            path = path.length() > 4 ? path.substring(5) : "";          // strip "/api/"
            List<String> parts = new ArrayList<>();
            for (String s : path.split("/")) if (!s.isEmpty()) parts.add(URLDecoder.decode(s, StandardCharsets.UTF_8));
            seg = parts.toArray(new String[0]);
            parse(ex.getRequestURI().getRawQuery());
            parse(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            pin = ex.getRequestHeaders().getFirst("X-Staff-Pin");
        }

        private void parse(String s) {
            if (s == null || s.isBlank()) return;
            for (String pair : s.split("&")) {
                if (pair.isEmpty()) continue;
                int i = pair.indexOf('=');
                String k = URLDecoder.decode(i < 0 ? pair : pair.substring(0, i), StandardCharsets.UTF_8);
                String v = i < 0 ? "" : URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8);
                params.put(k, v);
            }
        }

        String str(String k) {
            String v = params.get(k);
            return v == null || v.isBlank() ? null : v.trim();
        }

        int intParam(String k) {
            String v = str(k);
            if (v == null) throw new ApiException(400, "Missing parameter: " + k);
            return parseInt(v, k);
        }

        int idAt(int index) {
            if (seg.length <= index) throw new ApiException(400, "Missing id in URL");
            return parseInt(seg[index], "id");
        }

        void requireStaff() {
            if (pin == null || !pin.equals(STAFF_PIN)) throw new ApiException(401, "Staff PIN required");
        }

        boolean is(String m) {
            return method.equals(m);
        }
    }

    private static int parseInt(String v, String name) {
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            throw new ApiException(400, "Invalid number for " + name);
        }
    }

    // ------------------------------------------------------------------ handling
    @Override
    public void handle(HttpExchange ex) throws IOException {
        int status = 200;
        Object body;
        try {
            Req r = new Req(ex);
            body = route(r);
        } catch (ApiException e) {
            status = e.getStatus();
            body = Map.of("error", e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            status = 500;
            body = Map.of("error", "Database error: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            status = 500;
            body = Map.of("error", "Unexpected server error");
        }
        byte[] out = Json.write(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, out.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(out);
        }
    }

    // ------------------------------------------------------------------ routing
    private Object route(Req r) throws SQLException {
        if (r.seg.length == 0) throw new ApiException(404, "Unknown endpoint");
        switch (r.seg[0]) {
            case "health":
                return Map.of("ok", true);
            case "students":
                if (r.is("POST")) return svc.register(r.str("name"), r.str("email"), r.str("contact")).toMap();
                break;
            case "login":
                if (r.is("POST")) return svc.login(r.str("email")).toMap();
                break;
            case "categories":
                if (r.is("GET")) return svc.categories();
                break;
            case "queue":
                if (r.is("GET")) return svc.queueInfo();
                break;
            case "menu":
                return menu(r);
            case "orders":
                return orders(r);
            case "staff":
                return staff(r);
            default:
                break;
        }
        throw new ApiException(404, "Unknown endpoint");
    }

    private Object menu(Req r) throws SQLException {
        if (r.is("GET") && r.seg.length == 1) {
            List<Object> list = new ArrayList<>();
            svc.menu(r.str("q"), r.str("category")).forEach(m -> list.add(m.toMap()));
            return list;
        }
        r.requireStaff();
        if (r.is("POST") && r.seg.length == 1) {
            return svc.addMenuItem(r.str("name"), r.str("category"), r.str("price"), r.str("status")).toMap();
        }
        if (r.is("PUT") && r.seg.length == 2) {
            return svc.updateMenuItem(r.idAt(1), r.str("name"), r.str("category"), r.str("price"), r.str("status")).toMap();
        }
        if (r.is("DELETE") && r.seg.length == 2) {
            svc.deleteMenuItem(r.idAt(1));
            return Map.of("deleted", true);
        }
        throw new ApiException(404, "Unknown endpoint");
    }

    private Object orders(Req r) throws SQLException {
        if (r.is("POST") && r.seg.length == 1) {
            return svc.placeOrder(r.intParam("studentId"), parseItems(r.str("items")), r.str("paymentMethod"));
        }
        if (r.is("GET") && r.seg.length == 1) {
            return svc.ordersOfStudent(r.intParam("studentId"));
        }
        if (r.is("GET") && r.seg.length == 2) {
            return svc.getOrder(r.idAt(1));
        }
        if (r.is("PUT") && r.seg.length == 3 && r.seg[2].equals("cancel")) {
            return svc.cancelByStudent(r.idAt(1), r.intParam("studentId"));
        }
        throw new ApiException(404, "Unknown endpoint");
    }

    private Object staff(Req r) throws SQLException {
        if (r.seg.length == 2 && r.seg[1].equals("login") && r.is("POST")) {
            if (r.str("pin") == null || !r.str("pin").equals(STAFF_PIN)) throw new ApiException(401, "Wrong PIN");
            return Map.of("ok", true);
        }
        r.requireStaff();
        if (r.seg.length == 2 && r.is("GET")) {
            switch (r.seg[1]) {
                case "orders":
                    return svc.staffOrders(r.str("status"));
                case "stats":
                    return svc.stats();
                case "students": {
                    List<Object> list = new ArrayList<>();
                    svc.allStudents().forEach(s -> list.add(s.toMap()));
                    return list;
                }
                default:
                    break;
            }
        }
        if (r.seg.length == 4 && r.seg[1].equals("orders") && r.is("PUT")) {
            int id = r.idAt(2);
            if (r.seg[3].equals("status")) return svc.updateStatus(id, r.str("status"));
            if (r.seg[3].equals("payment")) return svc.updatePayment(id, r.str("status"), r.str("method"));
        }
        throw new ApiException(404, "Unknown endpoint");
    }

    /** "1:2,5:1" -> {1=2, 5=1} (menu item id : quantity). */
    static Map<Integer, Integer> parseItems(String s) {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        if (s == null) return map;
        for (String part : Arrays.asList(s.split(","))) {
            if (part.isBlank()) continue;
            String[] kv = part.split(":");
            if (kv.length != 2) throw new ApiException(400, "Items must look like 1:2,5:1");
            map.merge(parseInt(kv[0], "item id"), parseInt(kv[1], "quantity"), Integer::sum);
        }
        return map;
    }
}
