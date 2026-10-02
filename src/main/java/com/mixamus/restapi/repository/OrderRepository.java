package com.mixamus.restapi.repository;

import com.mixamus.restapi.entity.Order;
import com.mixamus.restapi.entity.OrderItem;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrderRepository {

    private static final String SELECT_ORDER_WITH_ITEMS = """
            SELECT o.id AS order_id, o.customer_name, o.status, o.total_amount,
                   i.id AS item_id, i.product_id, i.product_name, i.quantity,
                   i.unit_price, i.total_price
            FROM orders o
            LEFT JOIN order_items i ON i.order_id = o.id
            """;

    private final JdbcTemplate jdbcTemplate;

    public List<Order> findAll() {
        return queryOrders("");
    }

    public Optional<Order> findById(Long id) {
        return queryOrders("WHERE o.id = ?", id)
                .stream()
                .findFirst();
    }

    public Order save(Order order) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO orders (customer_name, status, total_amount)
                            VALUES (?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, order.getCustomerName());
            statement.setInt(2, order.getStatus().ordinal());
            statement.setBigDecimal(3, order.getTotalAmount());
            return statement;
        }, keyHolder);

        if (keyHolder.getKey() == null) {
            throw new IllegalStateException("Database did not return the generated order ID");
        }
        order.setId(keyHolder.getKey().longValue());

        for (OrderItem item : order.getItems()) {
            item.setOrder(order);
            jdbcTemplate.update("""
                            INSERT INTO order_items (
                                order_id, product_id, product_name, quantity, unit_price, total_price
                            ) VALUES (?, ?, ?, ?, ?, ?)
                            """,
                    order.getId(),
                    item.getProductId(),
                    item.getProductName(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.getTotalPrice());
        }
        return order;
    }

    public void delete(Order order) {
        jdbcTemplate.update("DELETE FROM order_items WHERE order_id = ?", order.getId());
        jdbcTemplate.update("DELETE FROM orders WHERE id = ?", order.getId());
    }

    private List<Order> queryOrders(String condition, Object... args) {
        String sql = SELECT_ORDER_WITH_ITEMS + condition + " ORDER BY o.id, i.id";
        ResultSetExtractor<List<Order>> extractor = resultSet -> {
            Map<Long, Order> orders = new LinkedHashMap<>();
            while (resultSet.next()) {
                long orderId = resultSet.getLong("order_id");
                Order order = orders.computeIfAbsent(orderId, ignored -> {
                    Order created = new Order();
                    created.setId(orderId);
                    return created;
                });
                order.setCustomerName(resultSet.getString("customer_name"));
                order.setStatus(orderStatusFromOrdinal(resultSet.getInt("status")));
                order.setTotalAmount(resultSet.getBigDecimal("total_amount"));

                long itemId = resultSet.getLong("item_id");
                if (!resultSet.wasNull()) {
                    OrderItem item = new OrderItem();
                    item.setId(itemId);
                    item.setOrder(order);
                    item.setProductId(resultSet.getLong("product_id"));
                    item.setProductName(resultSet.getString("product_name"));
                    item.setQuantity(resultSet.getInt("quantity"));
                    item.setUnitPrice(resultSet.getBigDecimal("unit_price"));
                    item.setTotalPrice(resultSet.getBigDecimal("total_price"));
                    order.getItems().add(item);
                }
            }
            return new ArrayList<>(orders.values());
        };
        return jdbcTemplate.query(sql, extractor, args);
    }

    private Order.OrderStatus orderStatusFromOrdinal(int ordinal) {
        Order.OrderStatus[] statuses = Order.OrderStatus.values();
        if (ordinal < 0 || ordinal >= statuses.length) {
            throw new DataRetrievalFailureException("Invalid order status ordinal: " + ordinal);
        }
        return statuses[ordinal];
    }
}
