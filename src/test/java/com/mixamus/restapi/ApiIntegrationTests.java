package com.mixamus.restapi;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:api-integration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
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
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM products");
    }

    @Test
    void productEndpointsSupportCreateReadUpdateAndDelete() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Keyboard", 49.99, 12)))
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
                .andExpect(jsonPath("$[0].id").value(productId));

        mockMvc.perform(put("/api/products/{productId}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Mechanical keyboard", 79.50, 8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mechanical keyboard"))
                .andExpect(jsonPath("$.price").value(79.50))
                .andExpect(jsonPath("$.stockQuantity").value(8));

        mockMvc.perform(delete("/api/products/{productId}", productId))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/products/{productId}", productId))
                .andExpect(status().isNotFound());
    }

    @Test
    void orderEndpointsReturnCalculatedOrderAndSupportReadAndDelete() throws Exception {
        long productId = createProduct("Mouse", 25.00, 10);
        String orderJson = """
                {
                  "customerName": "Customer",
                  "items": [{"productId": %d, "quantity": 3}]
                }
                """.formatted(productId);

        MvcResult createResult = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(75.0))
                .andExpect(jsonPath("$.items[0].productName").value("Mouse"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(25.0))
                .andExpect(jsonPath("$.items[0].totalPrice").value(75.0))
                .andReturn();
        long orderId = readId(createResult);

        mockMvc.perform(get("/api/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.items[0].quantity").value(3));
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(orderId));

        mockMvc.perform(delete("/api/orders/{orderId}", orderId))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/orders/{orderId}", orderId))
                .andExpect(status().isNotFound());
    }

    @Test
    void endpointsRejectInvalidRequestsAndUnknownResources() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "price": -1, "stockQuantity": 0}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/products/{productId}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerName": "Customer", "items": []}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerName": "Customer",
                                  "items": [{"productId": 9223372036854775807, "quantity": 1}]
                                }
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/orders/{orderId}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    private long createProduct(String name, double price, int stockQuantity) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(name, price, stockQuantity)))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    private long readId(MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertNotNull(response.get("id"));
        return response.get("id").longValue();
    }

    private String productJson(String name, double price, int stockQuantity) {
        return """
                {"name": "%s", "price": %s, "stockQuantity": %d}
                """.formatted(name, price, stockQuantity);
    }
}
