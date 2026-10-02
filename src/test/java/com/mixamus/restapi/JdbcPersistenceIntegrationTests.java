package com.mixamus.restapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mixamus.restapi.generated.model.OrderDto;
import com.mixamus.restapi.generated.model.OrderItemDto;
import com.mixamus.restapi.generated.model.ProductDto;
import com.mixamus.restapi.service.OrderService;
import com.mixamus.restapi.service.ProductService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
class JdbcPersistenceIntegrationTests {

    @Autowired
    private ProductService productService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM products");
    }

    @Test
    void productCrudPersistsGeneratedIdAndUpdatedValues() {
        ProductDto created = productService.create(product("Keyboard", 49.99, 12));

        assertNotNull(created.getId());
        assertEquals("Keyboard", productService.findById(created.getId()).getName());

        ProductDto update = product("Mechanical keyboard", 79.50, 8);
        ProductDto updated = productService.update(created.getId(), update);
        assertEquals("Mechanical keyboard", updated.getName());
        assertEquals(79.50, updated.getPrice(), 0.001);
        assertEquals(8, updated.getStockQuantity());

        productService.delete(created.getId());
        assertThrows(ResponseStatusException.class, () -> productService.findById(created.getId()));
    }

    @Test
    void orderCrudPersistsItemsAndDecrementsProductStock() {
        ProductDto product = productService.create(product("Mouse", 25.00, 10));
        OrderItemDto orderItem = new OrderItemDto();
        orderItem.setProductId(product.getId());
        orderItem.setQuantity(3);
        OrderDto request = new OrderDto();
        request.setCustomerName("Customer");
        request.setItems(List.of(orderItem));

        OrderDto created = orderService.create(request);

        assertNotNull(created.getId());
        assertEquals("PENDING", created.getStatus());
        assertEquals(75.0, created.getTotalAmount(), 0.001);
        assertEquals(1, created.getItems().size());
        assertEquals("Mouse", created.getItems().getFirst().getProductName());
        assertEquals(7, productService.findById(product.getId()).getStockQuantity());

        OrderDto loaded = orderService.findById(created.getId());
        assertEquals(1, orderService.findAll().size());
        assertEquals(3, loaded.getItems().getFirst().getQuantity());

        orderService.delete(created.getId());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_items", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class));
    }

    @Test
    void failedOrderCreationRollsBackInventoryAndOrderWrites() {
        ProductDto product = productService.create(product("Monitor", 199.00, 5));
        OrderItemDto validItem = new OrderItemDto();
        validItem.setProductId(product.getId());
        validItem.setQuantity(2);
        OrderItemDto missingProductItem = new OrderItemDto();
        missingProductItem.setProductId(Long.MAX_VALUE);
        missingProductItem.setQuantity(1);
        OrderDto request = new OrderDto();
        request.setCustomerName("Customer");
        request.setItems(List.of(validItem, missingProductItem));

        assertThrows(ResponseStatusException.class, () -> orderService.create(request));

        assertEquals(5, productService.findById(product.getId()).getStockQuantity());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class));
    }

    private ProductDto product(String name, double price, int stockQuantity) {
        ProductDto product = new ProductDto();
        product.setName(name);
        product.setPrice(price);
        product.setStockQuantity(stockQuantity);
        return product;
    }
}
