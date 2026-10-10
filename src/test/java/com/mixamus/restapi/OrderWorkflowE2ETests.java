package com.mixamus.restapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.mixamus.restapi.generated.model.OrderDto;
import com.mixamus.restapi.generated.model.OrderItemDto;
import com.mixamus.restapi.generated.model.ProductDto;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderWorkflowE2ETests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM products");
    }

    @Test
    void customerCanPlaceAndRetrieveOrderAndRejectedOrderDoesNotChangeInventory() {
        long keyboardId = createProduct("Keyboard", "50.00", 8);
        long mouseId = createProduct("Mouse", "20.00", 1);

        ResponseEntity<JsonNode> createdOrder = postJson("/api/orders",
                orderRequest("Customer", item(keyboardId, 2), item(mouseId, 1)));

        assertEquals(HttpStatus.CREATED, createdOrder.getStatusCode());
        JsonNode order = createdOrder.getBody();
        assertNotNull(order);
        long orderId = order.get("id").longValue();
        assertEquals("PENDING", order.get("status").asText());
        assertEquals(120.0, order.get("totalAmount").asDouble(), 0.001);
        assertEquals(2, order.get("items").size());
        assertEquals("Keyboard", order.get("items").get(0).get("productName").asText());
        assertEquals(50.0, order.get("items").get(0).get("unitPrice").asDouble(), 0.001);
        assertEquals(100.0, order.get("items").get(0).get("totalPrice").asDouble(), 0.001);
        assertEquals("Mouse", order.get("items").get(1).get("productName").asText());

        assertEquals(6, getJson("/api/products/" + keyboardId).getBody()
                .get("stockQuantity").asInt());
        assertEquals(0, getJson("/api/products/" + mouseId).getBody()
                .get("stockQuantity").asInt());
        assertEquals(orderId, getJson("/api/orders/" + orderId).getBody()
                .get("id").asLong());
        assertEquals(1, getJson("/api/orders").getBody().size());

        ResponseEntity<JsonNode> rejectedOrder = postJson("/api/orders",
                orderRequest("Another customer", item(keyboardId, 3), item(mouseId, 2)));

        assertEquals(HttpStatus.CONFLICT, rejectedOrder.getStatusCode());
        assertEquals(6, getJson("/api/products/" + keyboardId).getBody()
                .get("stockQuantity").asInt());
        assertEquals(0, getJson("/api/products/" + mouseId).getBody()
                .get("stockQuantity").asInt());
        assertEquals(1, getJson("/api/orders").getBody().size());

        assertEquals(HttpStatus.NO_CONTENT,
                restTemplate.exchange(uri("/api/orders/" + orderId), HttpMethod.DELETE,
                        HttpEntity.EMPTY, JsonNode.class).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, getJson("/api/orders/" + orderId).getStatusCode());
    }

    private long createProduct(String name, String price, int stockQuantity) {
        ResponseEntity<JsonNode> response = postJson("/api/products",
                new ProductDto(name, new BigDecimal(price), stockQuantity));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody().get("id").longValue();
    }

    private OrderDto orderRequest(String customerName, OrderItemDto... items) {
        OrderDto order = new OrderDto();
        order.setCustomerName(customerName);
        order.setItems(List.of(items));
        return order;
    }

    private OrderItemDto item(long productId, int quantity) {
        OrderItemDto item = new OrderItemDto();
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }

    private ResponseEntity<JsonNode> postJson(String path, Object request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(uri(path), new HttpEntity<>(request, headers), JsonNode.class);
    }

    private ResponseEntity<JsonNode> getJson(String path) {
        return restTemplate.getForEntity(uri(path), JsonNode.class);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
