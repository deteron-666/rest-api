package com.mixamus.restapi;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mixamus.restapi.generated.model.OrderDto;
import com.mixamus.restapi.generated.model.OrderItemDto;
import com.mixamus.restapi.generated.model.ProductDto;
import com.mixamus.restapi.service.OrderService;
import com.mixamus.restapi.service.ProductService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Данные отчёта:
 * <pre>
 * Keyboard 10.00 | Mouse 25.00 | Cable 5.00 | Webcam 7.00 (удалён из каталога)
 * #1 PAID       сейчас: Keyboard x3 (30), Mouse x1 (25)
 * #2 PENDING    сейчас: Keyboard x2 (20), Cable x4 (20)
 * #3 CANCELLED  сейчас: Mouse x10 (250)
 * #4 PAID       2020:   Mouse x2 (50)
 * #5 PENDING    сейчас: Webcam x1 (7)
 * </pre>
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class SalesReportIntegrationTests {

    private static final String REPORT = "/api/reports/product-sales";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductService productService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long keyboardId;
    private long mouseId;
    private long webcamId;

    @BeforeEach
    void prepareSales() {
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM products");

        keyboardId = product("Keyboard", "10.00");
        mouseId = product("Mouse", "25.00");
        long cableId = product("Cable", "5.00");
        webcamId = product("Webcam", "7.00");

        orderService.pay(order(item(keyboardId, 3), item(mouseId, 1)));
        order(item(keyboardId, 2), item(cableId, 4));
        orderService.cancel(order(item(mouseId, 10)));
        long oldOrderId = order(item(mouseId, 2));
        orderService.pay(oldOrderId);
        jdbcTemplate.update("UPDATE orders SET created_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse("2020-06-01T00:00:00Z")), oldOrderId);
        order(item(webcamId, 1));
        productService.delete(webcamId);
    }

    @Test
    void reportAggregatesSalesSortedByRevenueExcludingCancelledOrders() throws Exception {
        // Выручка: Mouse 25 + 50 = 75, Keyboard 30 + 20 = 50, Cable 20, Webcam 7; всего 152
        mockMvc.perform(get(REPORT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].productName").value("Mouse"))
                .andExpect(jsonPath("$[0].unitsSold").value(3))
                .andExpect(jsonPath("$[0].ordersCount").value(2))
                .andExpect(jsonPath("$[0].revenue").value(75.0))
                .andExpect(jsonPath("$[0].averageUnitPrice").value(25.0))
                .andExpect(jsonPath("$[0].revenueSharePercent").value(49.34))
                .andExpect(jsonPath("$[0].currentStock").value(97))
                .andExpect(jsonPath("$[1].productId").value(keyboardId))
                .andExpect(jsonPath("$[1].unitsSold").value(5))
                .andExpect(jsonPath("$[1].ordersCount").value(2))
                .andExpect(jsonPath("$[1].revenue").value(50.0))
                .andExpect(jsonPath("$[1].revenueSharePercent").value(32.89))
                .andExpect(jsonPath("$[1].currentStock").value(95))
                .andExpect(jsonPath("$[2].productName").value("Cable"))
                // Товар удалён: название из снимка в позиции заказа, остатка нет
                .andExpect(jsonPath("$[3].productId").value(webcamId))
                .andExpect(jsonPath("$[3].productName").value("Webcam"))
                .andExpect(jsonPath("$[3].currentStock").value(nullValue()));
    }

    @Test
    void reportFiltersByPeriod() throws Exception {
        // Без заказа 2020 года: Mouse 25, всего 102
        mockMvc.perform(get(REPORT).param("from", "2025-01-01T00:00:00Z"))
                .andExpect(jsonPath("$[0].productName").value("Keyboard"))
                .andExpect(jsonPath("$[0].revenue").value(50.0))
                .andExpect(jsonPath("$[0].revenueSharePercent").value(49.02))
                .andExpect(jsonPath("$[1].productName").value("Mouse"))
                .andExpect(jsonPath("$[1].revenue").value(25.0));

        mockMvc.perform(get(REPORT).param("to", "2021-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productName").value("Mouse"))
                .andExpect(jsonPath("$[0].revenue").value(50.0))
                .andExpect(jsonPath("$[0].revenueSharePercent").value(100.0));
    }

    @Test
    void reportFiltersByStatusesRevenueAndLimit() throws Exception {
        mockMvc.perform(get(REPORT).param("status", "CANCELLED"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productName").value("Mouse"))
                .andExpect(jsonPath("$[0].unitsSold").value(10))
                .andExpect(jsonPath("$[0].revenue").value(250.0));

        mockMvc.perform(get(REPORT).param("status", "PAID", "CANCELLED"))
                .andExpect(jsonPath("$[0].productName").value("Mouse"))
                .andExpect(jsonPath("$[0].revenue").value(325.0));

        // HAVING sum(total_price) >= 30
        mockMvc.perform(get(REPORT).param("minRevenue", "30"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].productName").value("Keyboard"));

        mockMvc.perform(get(REPORT).param("limit", "1"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productName").value("Mouse"));

        mockMvc.perform(get(REPORT).param("limit", "0"))
                .andExpect(status().isBadRequest());
    }

    private long product(String name, String price) {
        return productService.create(new ProductDto(name, new BigDecimal(price), 100)).getId();
    }

    private long order(OrderItemDto... items) {
        return orderService.create(new OrderDto("Customer", List.of(items))).getId();
    }

    private OrderItemDto item(long productId, int quantity) {
        return new OrderItemDto(productId, quantity);
    }
}
