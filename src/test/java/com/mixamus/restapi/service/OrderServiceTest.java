package com.mixamus.restapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mixamus.restapi.entity.Order;
import com.mixamus.restapi.entity.Product;
import com.mixamus.restapi.exception.ConflictException;
import com.mixamus.restapi.exception.InsufficientStockException;
import com.mixamus.restapi.exception.NotFoundException;
import com.mixamus.restapi.generated.model.OrderDto;
import com.mixamus.restapi.generated.model.OrderItemDto;
import com.mixamus.restapi.generated.model.OrderStatus;
import com.mixamus.restapi.repository.OrderRepository;
import com.mixamus.restapi.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createLocksProductsDecreasesStockAndCalculatesTotal() {
        Product keyboard = product(1L, "Keyboard", "50.00", 10);
        Product mouse = product(2L, "Mouse", "20.00", 10);
        when(productRepository.findAllByIdForUpdate(Set.of(1L, 2L))).thenReturn(List.of(keyboard, mouse));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OrderDto created = orderService.create(new OrderDto(" Customer ", List.of(
                new OrderItemDto(2L, 1), new OrderItemDto(1L, 2), new OrderItemDto(2L, 3))));

        assertEquals(8, keyboard.getStockQuantity());
        assertEquals(6, mouse.getStockQuantity());
        assertEquals("Customer", created.getCustomerName());
        assertEquals(OrderStatus.PENDING, created.getStatus());
        assertEquals(new BigDecimal("180.00"), created.getTotalAmount());
        assertEquals(3, created.getItems().size());
    }

    @Test
    void createFailsWithoutSavingWhenStockIsInsufficient() {
        when(productRepository.findAllByIdForUpdate(Set.of(1L)))
                .thenReturn(List.of(product(1L, "Keyboard", "50.00", 10)));

        assertThrows(InsufficientStockException.class,
                () -> orderService.create(new OrderDto("Customer", List.of(new OrderItemDto(1L, 11)))));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createFailsWhenProductDoesNotExist() {
        when(productRepository.findAllByIdForUpdate(Set.of(1L))).thenReturn(List.of());

        assertThrows(NotFoundException.class,
                () -> orderService.create(new OrderDto("Customer", List.of(new OrderItemDto(1L, 1)))));
    }

    @Test
    void cancelReleasesReservedStock() {
        Product keyboard = product(1L, "Keyboard", "50.00", 10);
        Order order = order(keyboard, 2);
        when(orderRepository.findWithItemsById(5L)).thenReturn(Optional.of(order));
        when(productRepository.findAllByIdForUpdate(Set.of(1L))).thenReturn(List.of(keyboard));

        OrderDto cancelled = orderService.cancel(5L);

        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        assertEquals(12, keyboard.getStockQuantity());
    }

    @Test
    void paidOrderCannotBeDeletedOrCancelled() {
        Order order = order(product(1L, "Keyboard", "50.00", 10), 2);
        order.pay();
        when(orderRepository.findWithItemsById(5L)).thenReturn(Optional.of(order));

        assertThrows(ConflictException.class, () -> orderService.delete(5L));
        assertThrows(ConflictException.class, () -> orderService.cancel(5L));
        verify(orderRepository, never()).delete(any());
    }

    private static Product product(Long id, String name, String price, int stock) {
        Product product = new Product(name, null, new BigDecimal(price), stock);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    private static Order order(Product product, int quantity) {
        Order order = new Order("Customer");
        order.addItem(product, quantity);
        ReflectionTestUtils.setField(order, "id", 5L);
        return order;
    }
}
