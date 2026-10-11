package com.mixamus.restapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM products");
    }

    @Test
    void productEndpointsSupportCreateReadUpdateAndDelete() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Keyboard", "49.99", 12)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Keyboard"))
                .andExpect(jsonPath("$.price").value(49.99))
                .andExpect(jsonPath("$.stockQuantity").value(12))
                .andReturn();
        long productId = readId(createResult);

        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId))
                .andExpect(jsonPath("$.name").value("Keyboard"));
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].id").value(productId));

        mockMvc.perform(put("/api/products/{productId}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Mechanical keyboard", "79.50", 8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mechanical keyboard"))
                .andExpect(jsonPath("$.price").value(79.50))
                .andExpect(jsonPath("$.stockQuantity").value(8));

        mockMvc.perform(delete("/api/products/{productId}", productId))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/products/{productId}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Ghost", "1.00", 1)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/products/{productId}", productId))
                .andExpect(status().isNotFound());
    }

    @Test
    void productPriceKeepsExactDecimalValue() throws Exception {
        long productId = createProduct("Cable", "0.10", 3);

        // 0.1 + 0.2 в double дало бы 0.30000000000000004
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(productId, 3)))
                .andExpect(status().isCreated())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"totalAmount\":0.30")));
    }

    @Test
    void productListSupportsFiltersAndPagination() throws Exception {
        createProduct("Wireless mouse", "25.00", 10);
        createProduct("Gaming mouse", "60.00", 0);
        createProduct("Keyboard", "80.00", 5);

        mockMvc.perform(get("/api/products").param("name", "MOUSE"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/products").param("name", "mouse").param("inStock", "true"))
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].name").value("Wireless mouse"));

        mockMvc.perform(get("/api/products").param("minPrice", "50").param("maxPrice", "80"))
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$[0].name").value("Gaming mouse"))
                .andExpect(jsonPath("$[1].name").value("Keyboard"));

        // Спецсимволы LIKE экранируются и ищутся как обычный текст
        mockMvc.perform(get("/api/products").param("name", "%"))
                .andExpect(header().string("X-Total-Count", "0"));
        mockMvc.perform(get("/api/products").param("name", "_"))
                .andExpect(header().string("X-Total-Count", "0"));

        mockMvc.perform(get("/api/products").param("page", "1").param("size", "2"))
                .andExpect(header().string("X-Total-Count", "3"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Keyboard"));
    }

    @Test
    void orderEndpointsReturnCalculatedOrderAndSupportReadAndDelete() throws Exception {
        long productId = createProduct("Mouse", "25.00", 10);

        MvcResult createResult = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(productId, 3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(75.0))
                .andExpect(jsonPath("$.items[0].productName").value("Mouse"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(25.0))
                .andExpect(jsonPath("$.items[0].totalPrice").value(75.0))
                .andReturn();
        long orderId = readId(createResult);
        expectStock(productId, 7);

        mockMvc.perform(get("/api/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.items[0].quantity").value(3));
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].id").value(orderId));

        mockMvc.perform(delete("/api/orders/{orderId}", orderId))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/orders/{orderId}", orderId))
                .andExpect(status().isNotFound());
        // Удаление PENDING-заказа возвращает зарезервированный товар
        expectStock(productId, 10);
    }

    @Test
    void orderLifecycleSupportsPayAndCancel() throws Exception {
        long productId = createProduct("Mouse", "25.00", 10);
        long paidOrderId = createOrder(productId, 2);
        long cancelledOrderId = createOrder(productId, 3);
        expectStock(productId, 5);

        mockMvc.perform(post("/api/orders/{orderId}/pay", paidOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
        mockMvc.perform(post("/api/orders/{orderId}/cancel", paidOrderId))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/orders/{orderId}", paidOrderId))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/orders/{orderId}/cancel", cancelledOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        expectStock(productId, 8);
        mockMvc.perform(post("/api/orders/{orderId}/pay", cancelledOrderId))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(get("/api/orders").param("status", "PAID"))
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].id").value(paidOrderId));

        // Отменённый заказ удаляется без повторного возврата остатка
        mockMvc.perform(delete("/api/orders/{orderId}", cancelledOrderId))
                .andExpect(status().isNoContent());
        expectStock(productId, 8);

        mockMvc.perform(post("/api/orders/{orderId}/pay", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void clientCannotSetOrderStatus() throws Exception {
        long productId = createProduct("Mouse", "25.00", 10);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerName": "Customer",
                                  "status": "PAID",
                                  "items": [{"productId": %d, "quantity": 1}]
                                }
                                """.formatted(productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void endpointsRejectInvalidRequestsAndUnknownResources() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "price": -1, "stockQuantity": 0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.price").exists());

        mockMvc.perform(get("/api/products/{productId}", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Product with id " + Long.MAX_VALUE + " not found"));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerName": "Customer", "items": []}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(Long.MAX_VALUE, 1)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/orders/{orderId}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void boundaryValuesAreRejectedWithBadRequestInsteadOfServerError() throws Exception {
        expectBadProduct("""
                {"name": "Keyboard", "description": "%s", "price": 1, "stockQuantity": 1}
                """.formatted("x".repeat(501)), "description");
        expectBadProduct("""
                {"name": "Keyboard", "price": 1e20, "stockQuantity": 1}
                """, "price");
        expectBadProduct("""
                {"name": "Keyboard", "price": 10.999, "stockQuantity": 1}
                """, "price");
        expectBadProduct("""
                {"name": "   ", "price": 1, "stockQuantity": 1}
                """, "name");
        expectBadProduct("""
                {"name": "Keyboard", "price": 1, "stockQuantity": 2000000}
                """, "stockQuantity");

        long productId = createProduct("Mouse", "25.00", 10);
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(productId, 100000)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerName": "  ", "items": [{"productId": %d, "quantity": 1}]}
                                """.formatted(productId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.customerName").exists());

        mockMvc.perform(get("/api/products").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.size").exists());
        mockMvc.perform(get("/api/orders").param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void orderWithMoreItemsThanInStockIsRejectedWithConflict() throws Exception {
        long productId = createProduct("Mouse", "25.00", 2);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(productId, 3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Not enough stock for product Mouse"));

        // Один товар дважды в заказе: суммарно 3 > 2
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerName": "Customer",
                                  "items": [
                                    {"productId": %1$d, "quantity": 2},
                                    {"productId": %1$d, "quantity": 1}
                                  ]
                                }
                                """.formatted(productId)))
                .andExpect(status().isConflict());
        expectStock(productId, 2);
    }

    @Test
    void productUpdateWithStaleVersionDoesNotOverwriteReservedStock() throws Exception {
        long productId = createProduct("Mouse", "25.00", 10);
        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(jsonPath("$.version").value(0));

        // Клиент прочитал товар с версией 0, а затем заказ зарезервировал 3 шт. и изменил версию
        createOrder(productId, 3);

        mockMvc.perform(put("/api/products/{productId}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Wireless mouse", "25.00", 10, 0)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        expectStock(productId, 7);

        mockMvc.perform(put("/api/products/{productId}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Wireless mouse", "25.00", 7, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Wireless mouse"))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void orderWithNullItemIsRejectedWithBadRequest() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerName": "Customer", "items": [null]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors['items[0]']").exists());
    }

    private void expectBadProduct(String json, String field) throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors." + field).exists());
    }

    private void expectStock(long productId, int expected) throws Exception {
        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(jsonPath("$.stockQuantity").value(expected));
    }

    private long createProduct(String name, String price, int stockQuantity) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(name, price, stockQuantity)))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    private long createOrder(long productId, int quantity) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(productId, quantity)))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    private long readId(MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertNotNull(response.get("id"));
        return response.get("id").longValue();
    }

    private String productJson(String name, String price, int stockQuantity) {
        return """
                {"name": "%s", "price": %s, "stockQuantity": %d}
                """.formatted(name, price, stockQuantity);
    }

    private String productJson(String name, String price, int stockQuantity, long version) {
        return """
                {"name": "%s", "price": %s, "stockQuantity": %d, "version": %d}
                """.formatted(name, price, stockQuantity, version);
    }

    private String orderJson(long productId, int quantity) {
        return """
                {
                  "customerName": "Customer",
                  "items": [{"productId": %d, "quantity": %d}]
                }
                """.formatted(productId, quantity);
    }
}
