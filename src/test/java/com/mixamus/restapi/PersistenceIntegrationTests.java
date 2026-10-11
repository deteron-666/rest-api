package com.mixamus.restapi;

import com.mixamus.restapi.entity.Product;
import com.mixamus.restapi.exception.InsufficientStockException;
import com.mixamus.restapi.exception.NotFoundException;
import com.mixamus.restapi.generated.model.OrderDto;
import com.mixamus.restapi.generated.model.OrderItemDto;
import com.mixamus.restapi.generated.model.OrderStatus;
import com.mixamus.restapi.generated.model.ProductDto;
import com.mixamus.restapi.repository.OrderFilter;
import com.mixamus.restapi.repository.ProductRepository;
import com.mixamus.restapi.service.OrderService;
import com.mixamus.restapi.service.ProductService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("test")
@SpringBootTest
class PersistenceIntegrationTests {

    @Autowired
    private ProductService productService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM products");
    }

    @Test
    void productCrudPersistsGeneratedIdAndUpdatedValues() {
        ProductDto created = productService.create(product("Keyboard", "49.99", 12));

        assertNotNull(created.getId());
        assertEquals("Keyboard", productService.findById(created.getId()).getName());

        ProductDto updated = productService.update(created.getId(), product("Mechanical keyboard", "79.5", 8));
        assertEquals("Mechanical keyboard", updated.getName());
        assertEquals(new BigDecimal("79.50"), productService.findById(created.getId()).getPrice());
        assertEquals(8, updated.getStockQuantity());

        productService.delete(created.getId());
        assertThrows(NotFoundException.class, () -> productService.findById(created.getId()));
    }

    @Test
    void orderCrudPersistsItemsAndDecrementsProductStock() {
        ProductDto product = productService.create(product("Mouse", "25.00", 10));

        OrderDto created = orderService.create(order(item(product.getId(), 3)));

        assertNotNull(created.getId());
        assertEquals(OrderStatus.PENDING, created.getStatus());
        assertEquals(new BigDecimal("75.00"), created.getTotalAmount());
        assertEquals(1, created.getItems().size());
        assertEquals("Mouse", created.getItems().getFirst().getProductName());
        assertEquals(7, productService.findById(product.getId()).getStockQuantity());

        OrderDto loaded = orderService.findById(created.getId());
        assertEquals(1, orderService.findAll(OrderFilter.none(), 0, 20).items().size());
        assertEquals(3, loaded.getItems().getFirst().getQuantity());
        assertEquals("PENDING", jdbcTemplate.queryForObject("SELECT status FROM orders", String.class));

        orderService.delete(created.getId());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_items", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class));
        assertEquals(10, productService.findById(product.getId()).getStockQuantity());
    }

    @Test
    void failedOrderCreationRollsBackInventoryAndOrderWrites() {
        ProductDto monitor = productService.create(product("Monitor", "199.00", 5));
        ProductDto cable = productService.create(product("Cable", "5.00", 1));

        assertThrows(InsufficientStockException.class,
                () -> orderService.create(order(item(monitor.getId(), 2), item(cable.getId(), 2))));
        assertThrows(NotFoundException.class,
                () -> orderService.create(order(item(monitor.getId(), 2), item(Long.MAX_VALUE, 1))));

        assertEquals(5, productService.findById(monitor.getId()).getStockQuantity());
        assertEquals(1, productService.findById(cable.getId()).getStockQuantity());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class));
    }

    @Test
    void concurrentOrdersNeverOversellStock() throws Exception {
        int stock = 5;
        int buyers = 20;
        ProductDto product = productService.create(product("Limited edition", "10.00", stock));

        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            tasks.add(() -> {
                start.await();
                try {
                    orderService.create(order(item(product.getId(), 1)));
                    return true;
                } catch (InsufficientStockException exception) {
                    return false;
                }
            });
        }

        int succeeded = 0;
        try (ExecutorService executor = Executors.newFixedThreadPool(buyers)) {
            List<Future<Boolean>> results = tasks.stream().map(executor::submit).toList();
            start.countDown();
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    succeeded++;
                }
            }
        }

        assertEquals(stock, succeeded);
        assertEquals(0, productService.findById(product.getId()).getStockQuantity());
        assertEquals(stock, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class));
    }

    @Test
    void productUpdateFailsIfConcurrentOrderChangedStockAfterProductWasRead() {
        ProductDto product = productService.create(product("Mouse", "10.00", 10));

        assertThrows(OptimisticLockingFailureException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            Product loaded = productRepository.findById(product.getId()).orElseThrow();
            // Пока эта транзакция держит товар с остатком 10, другая создаёт заказ и списывает 3 шт.
            CompletableFuture.runAsync(() -> orderService.create(order(item(product.getId(), 3)))).join();
            // Как PUT: меняется название, а остаток записывается тот, что был прочитан до заказа
            loaded.update("Wireless mouse", null, new BigDecimal("10.00"), 10);
        }));

        assertEquals(7, productService.findById(product.getId()).getStockQuantity());
    }

    @Test
    void everyConnectionHasLockTimeout() {
        assertEquals("5s", jdbcTemplate.queryForObject("SHOW lock_timeout", String.class));
    }

    @Test
    void orderWaitingForLockedProductFailsWithConcurrencyFailureInsteadOfHanging() throws Exception {
        ProductDto product = productService.create(product("Mouse", "10.00", 10));
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        // Другая транзакция держит блокировку товара (SELECT ... FOR UPDATE) и не завершается
        CompletableFuture<Void> lockHolder = CompletableFuture.runAsync(() -> transactionTemplate.executeWithoutResult(status -> {
            productRepository.findAllByIdForUpdate(List.of(product.getId()));
            locked.countDown();
            await(release);
        }));
        try {
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            assertThrows(ConcurrencyFailureException.class, () -> transactionTemplate.executeWithoutResult(status -> {
                // Тот же lock_timeout, что задан в application.yaml, но короче, чтобы тест не ждал 5 секунд
                jdbcTemplate.execute("SET LOCAL lock_timeout = '200ms'");
                orderService.create(order(item(product.getId(), 1)));
            }));
        } finally {
            release.countDown();
            lockHolder.join();
        }

        assertEquals(10, productService.findById(product.getId()).getStockQuantity());
    }

    @Test
    void orderListLoadsItemsWithoutNPlusOneQueries() {
        ProductDto product = productService.create(product("Mouse", "10.00", 100));
        for (int i = 0; i < 10; i++) {
            orderService.create(order(item(product.getId(), 1), item(product.getId(), 2)));
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        List<OrderDto> orders = orderService.findAll(OrderFilter.none(), 0, 5).items();

        assertEquals(5, orders.size());
        assertEquals(2, orders.getFirst().getItems().size());
        // id страницы + заказы с позициями (JOIN FETCH) + count; без N+1 было бы 1 + 5 запросов на позиции
        assertEquals(3, statistics.getPrepareStatementCount());
    }

    @Test
    void databaseConstraintsProtectInvariants() {
        assertThrows(Exception.class, () -> jdbcTemplate.update(
                "INSERT INTO products (name, price, stock_quantity) VALUES ('Broken', 1, -1)"));
        assertThrows(Exception.class, () -> jdbcTemplate.update(
                "INSERT INTO orders (customer_name, status, total_amount) VALUES ('Customer', 'UNKNOWN', 0)"));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private ProductDto product(String name, String price, int stockQuantity) {
        return new ProductDto(name, new BigDecimal(price), stockQuantity);
    }

    private OrderDto order(OrderItemDto... items) {
        return new OrderDto("Customer", List.of(items));
    }

    private OrderItemDto item(long productId, int quantity) {
        return new OrderItemDto(productId, quantity);
    }
}
