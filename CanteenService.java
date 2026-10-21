package com.queueless.canteen.service;

import com.queueless.canteen.dao.MenuDao;
import com.queueless.canteen.dao.OrderDao;
import com.queueless.canteen.dao.PaymentDao;
import com.queueless.canteen.dao.StudentDao;
import com.queueless.canteen.model.MenuItem;
import com.queueless.canteen.model.Order;
import com.queueless.canteen.model.Student;
import com.queueless.canteen.util.Db;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** All business rules: registration, menu, ordering, tokens, waiting time, status and payment handling. */
public class CanteenService {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+\\- ]{7,15}$");
    private static final Set<String> METHODS = Set.of("CASH", "UPI", "CARD");
    private static final Set<String> PAY_STATUS = Set.of("PENDING", "SUCCESS", "FAILED");
    private static final Set<String> ORDER_STATUS = Set.of("PLACED", "PREPARING", "READY", "COMPLETED", "CANCELLED");
    private static final Object ORDER_LOCK = new Object();

    private final StudentDao students = new StudentDao();
    private final MenuDao menu = new MenuDao();
    private final OrderDao orders = new OrderDao();
    private final PaymentDao payments = new PaymentDao();

    // ================================================================ students

    public Student register(String name, String email, String contact) throws SQLException {
        if (name == null || name.isBlank()) throw new ApiException(400, "Name is required");
        if (email == null || !EMAIL.matcher(email).matches()) throw new ApiException(400, "Enter a valid email address");
        if (contact == null || !PHONE.matcher(contact).matches()) throw new ApiException(400, "Enter a valid contact number");
        try (Connection c = Db.get()) {
            if (students.findByEmail(c, email) != null) {
                throw new ApiException(409, "This email is already registered - just log in");
            }
            try {
                return students.insert(c, name.trim(), email.trim().toLowerCase(), contact.trim());
            } catch (SQLIntegrityConstraintViolationException e) {
                throw new ApiException(409, "This email is already registered - just log in");
            }
        }
    }

    public Student login(String email) throws SQLException {
        if (email == null || email.isBlank()) throw new ApiException(400, "Email is required");
        try (Connection c = Db.get()) {
            Student s = students.findByEmail(c, email.trim());
            if (s == null) throw new ApiException(404, "No student found with that email - please register first");
            return s;
        }
    }

    public List<Student> allStudents() throws SQLException {
        try (Connection c = Db.get()) {
            return students.findAll(c);
        }
    }

    // ================================================================ menu

    public List<MenuItem> menu(String text, String category) throws SQLException {
        try (Connection c = Db.get()) {
            return menu.search(c, text, category);
        }
    }

    public List<String> categories() throws SQLException {
        try (Connection c = Db.get()) {
            return menu.categories(c);
        }
    }

    public MenuItem addMenuItem(String name, String category, String price, String status) throws SQLException {
        MenuItem v = validateMenu(name, category, price, status);
        try (Connection c = Db.get()) {
            int id = menu.insert(c, v.itemName(), v.category(), v.price(), v.availabilityStatus());
            return menu.findById(c, id);
        }
    }

    public MenuItem updateMenuItem(int id, String name, String category, String price, String status) throws SQLException {
        MenuItem v = validateMenu(name, category, price, status);
        try (Connection c = Db.get()) {
            if (!menu.update(c, id, v.itemName(), v.category(), v.price(), v.availabilityStatus())) {
                if (menu.findById(c, id) == null) throw new ApiException(404, "Menu item not found");
            }
            return menu.findById(c, id);
        }
    }

    public void deleteMenuItem(int id) throws SQLException {
        try (Connection c = Db.get()) {
            if (menu.findById(c, id) == null) throw new ApiException(404, "Menu item not found");
            try {
                menu.delete(c, id);
            } catch (SQLIntegrityConstraintViolationException e) {
                throw new ApiException(409, "This item appears in past orders and cannot be deleted - mark it UNAVAILABLE instead");
            }
        }
    }

    private MenuItem validateMenu(String name, String category, String price, String status) {
        if (name == null || name.isBlank()) throw new ApiException(400, "Item name is required");
        if (category == null || category.isBlank()) throw new ApiException(400, "Category is required");
        BigDecimal p;
        try {
            p = new BigDecimal(price == null ? "" : price.trim());
        } catch (NumberFormatException e) {
            throw new ApiException(400, "Price must be a number");
        }
        if (p.signum() < 0 || p.compareTo(new BigDecimal("100000")) > 0) throw new ApiException(400, "Price is out of range");
        String st = status == null || status.isBlank() ? "AVAILABLE" : status.trim().toUpperCase();
        if (!st.equals("AVAILABLE") && !st.equals("UNAVAILABLE")) throw new ApiException(400, "Status must be AVAILABLE or UNAVAILABLE");
        return new MenuItem(0, name.trim(), category.trim(), p.setScale(2, java.math.RoundingMode.HALF_UP), st);
    }

    // ================================================================ ordering

    /** Waiting time: 3 min base + 2 min for every order already in the queue + 1 min per item in this order. */
    static int estimate(int pendingAhead, int itemCount) {
        return 3 + 2 * pendingAhead + itemCount;
    }

    public Map<String, Object> queueInfo() throws SQLException {
        try (Connection c = Db.get()) {
            int pending = orders.countPending(c);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("pendingOrders", pending);
            m.put("estimatedWaitForNewOrder", estimate(pending, 1));
            return m;
        }
    }

    /** itemQty = menu item id -> quantity. Creates the order, its items and a PENDING payment in one transaction. */
    public Map<String, Object> placeOrder(int studentId, Map<Integer, Integer> itemQty, String method) throws SQLException {
        String pay = method == null ? "" : method.trim().toUpperCase();
        if (!METHODS.contains(pay)) throw new ApiException(400, "Payment method must be CASH, UPI or CARD");
        if (itemQty == null || itemQty.isEmpty()) throw new ApiException(400, "Your cart is empty");

        synchronized (ORDER_LOCK) {
            try (Connection c = Db.get()) {
                if (students.findById(c, studentId) == null) throw new ApiException(404, "Student not found - please log in again");
                c.setAutoCommit(false);
                try {
                    BigDecimal total = BigDecimal.ZERO;
                    int totalQty = 0;
                    List<Object[]> lines = new ArrayList<>();
                    for (Map.Entry<Integer, Integer> e : itemQty.entrySet()) {
                        int qty = e.getValue();
                        if (qty < 1 || qty > 20) throw new ApiException(400, "Quantity must be between 1 and 20");
                        MenuItem m = menu.findById(c, e.getKey());
                        if (m == null) throw new ApiException(404, "A menu item in your cart no longer exists");
                        if (!m.isAvailable()) throw new ApiException(409, m.itemName() + " is currently unavailable - remove it from your cart");
                        BigDecimal sub = m.price().multiply(BigDecimal.valueOf(qty));
                        total = total.add(sub);
                        totalQty += qty;
                        lines.add(new Object[]{m.itemId(), qty, m.price(), sub});
                    }
                    int token = orders.nextToken(c, LocalDate.now().atStartOfDay());
                    int est = estimate(orders.countPending(c), totalQty);
                    int orderId = orders.insert(c, studentId, LocalDateTime.now(), total, token, est);
                    for (Object[] l : lines) {
                        orders.insertItem(c, orderId, (Integer) l[0], (Integer) l[1], (BigDecimal) l[2], (BigDecimal) l[3]);
                    }
                    payments.insertPending(c, orderId, pay, total);
                    c.commit();
                    return view(c, orders.findById(c, orderId));
                } catch (RuntimeException | SQLException e) {
                    c.rollback();
                    throw e;
                }
            }
        }
    }

    public Map<String, Object> getOrder(int id) throws SQLException {
        try (Connection c = Db.get()) {
            Order o = orders.findById(c, id);
            if (o == null) throw new ApiException(404, "Order not found");
            return view(c, o);
        }
    }

    public List<Object> ordersOfStudent(int studentId) throws SQLException {
        try (Connection c = Db.get()) {
            return views(c, orders.findByStudent(c, studentId));
        }
    }

    /** filter: ACTIVE (placed/preparing/ready), ALL, or a single status name. */
    public List<Object> staffOrders(String filter) throws SQLException {
        String f = filter == null || filter.isBlank() ? "ACTIVE" : filter.trim().toUpperCase();
        List<String> statuses;
        boolean oldestFirst = false;
        if (f.equals("ACTIVE")) {
            statuses = List.of("PLACED", "PREPARING", "READY");
            oldestFirst = true;
        } else if (f.equals("ALL")) {
            statuses = null;
        } else if (ORDER_STATUS.contains(f)) {
            statuses = List.of(f);
        } else {
            throw new ApiException(400, "Unknown status filter");
        }
        try (Connection c = Db.get()) {
            return views(c, orders.findByStatuses(c, statuses, oldestFirst));
        }
    }

    /** Student cancels their own order - only while it is still PLACED. */
    public Map<String, Object> cancelByStudent(int orderId, int studentId) throws SQLException {
        try (Connection c = Db.get()) {
            Order o = orders.findById(c, orderId);
            if (o == null || o.studentId() != studentId) throw new ApiException(404, "Order not found");
            if (!"PLACED".equals(o.orderStatus())) {
                throw new ApiException(409, "The kitchen has already started this order - it can no longer be cancelled");
            }
            return cancel(c, o);
        }
    }

    // ================================================================ staff actions

    public Map<String, Object> updateStatus(int orderId, String to) throws SQLException {
        String target = to == null ? "" : to.trim().toUpperCase();
        if (!ORDER_STATUS.contains(target)) throw new ApiException(400, "Unknown order status");
        try (Connection c = Db.get()) {
            Order o = orders.findById(c, orderId);
            if (o == null) throw new ApiException(404, "Order not found");
            String from = o.orderStatus();
            boolean ok = switch (target) {
                case "PREPARING" -> from.equals("PLACED");
                case "READY" -> from.equals("PREPARING");
                case "COMPLETED" -> from.equals("READY");
                case "CANCELLED" -> from.equals("PLACED") || from.equals("PREPARING");
                default -> false;
            };
            if (!ok) throw new ApiException(409, "Cannot move an order from " + from + " to " + target);
            if (target.equals("CANCELLED")) return cancel(c, o);
            if (target.equals("COMPLETED") && (o.payment() == null || !"SUCCESS".equals(o.payment().paymentStatus()))) {
                throw new ApiException(409, "Record a successful payment before completing the order");
            }
            orders.updateStatus(c, orderId, target);
            return view(c, orders.findById(c, orderId));
        }
    }

    public Map<String, Object> updatePayment(int orderId, String status, String method) throws SQLException {
        String st = status == null ? "" : status.trim().toUpperCase();
        if (!PAY_STATUS.contains(st)) throw new ApiException(400, "Payment status must be PENDING, SUCCESS or FAILED");
        String m = method == null || method.isBlank() ? null : method.trim().toUpperCase();
        if (m != null && !METHODS.contains(m)) throw new ApiException(400, "Payment method must be CASH, UPI or CARD");
        try (Connection c = Db.get()) {
            Order o = orders.findById(c, orderId);
            if (o == null) throw new ApiException(404, "Order not found");
            if (o.orderStatus().equals("CANCELLED") || o.orderStatus().equals("COMPLETED")) {
                throw new ApiException(409, "This order is already " + o.orderStatus() + " - payment can no longer be changed");
            }
            payments.update(c, orderId, st, m, st.equals("PENDING") ? null : LocalDateTime.now());
            return view(c, orders.findById(c, orderId));
        }
    }

    public Map<String, Object> stats() throws SQLException {
        LocalDateTime today = LocalDate.now().atStartOfDay();
        try (Connection c = Db.get()) {
            Map<String, Integer> byStatus = orders.countByStatusSince(c, today);
            int total = 0;
            for (int v : byStatus.values()) total += v;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ordersToday", total);
            m.put("byStatus", byStatus);
            m.put("pendingOrders", orders.countPending(c));
            m.put("revenueToday", payments.revenueSince(c, today));
            m.put("menuItems", menu.count(c));
            m.put("students", students.count(c));
            return m;
        }
    }

    // ================================================================ helpers

    private Map<String, Object> cancel(Connection c, Order o) throws SQLException {
        c.setAutoCommit(false);
        try {
            orders.updateStatus(c, o.orderId(), "CANCELLED");
            if (o.payment() != null && "PENDING".equals(o.payment().paymentStatus())) {
                payments.update(c, o.orderId(), "FAILED", null, LocalDateTime.now());
            }
            c.commit();
        } catch (SQLException | RuntimeException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(true);
        }
        return view(c, orders.findById(c, o.orderId()));
    }

    /** Order as a JSON-ready map with a live waiting-time estimate (0 once it is READY/COMPLETED/CANCELLED). */
    private Map<String, Object> view(Connection c, Order o) throws SQLException {
        int wait = o.isPending() ? estimate(orders.countAhead(c, o.orderId()), o.totalQuantity()) : 0;
        return o.toMap(wait);
    }

    private List<Object> views(Connection c, List<Order> list) throws SQLException {
        List<Object> out = new ArrayList<>();
        for (Order o : list) out.add(view(c, o));
        return out;
    }
}
