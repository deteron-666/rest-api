package com.mixamus.restapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mixamus.restapi.entity.Order;
import com.mixamus.restapi.entity.OrderItem;
import com.mixamus.restapi.entity.Product;
import com.mixamus.restapi.exception.ConflictException;
import com.mixamus.restapi.exception.InsufficientStockException;
import com.mixamus.restapi.generated.model.OrderDto;
import com.mixamus.restapi.generated.model.OrderItemDto;
import com.mixamus.restapi.generated.model.OrderStatus;
import com.mixamus.restapi.repository.OrderRepository;
import com.mixamus.restapi.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Product KEYBOARD = new Product(1L, "Keyboard", null, new BigDecimal("50.00"), 10);
    private static final Product MOUSE = new Product(2L, "Mouse", null, new BigDecimal("20.00"), 10);

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createReservesSummedQuantitiesInAscendingProductOrder() {
        when(productRepository.findAllByIds(any())).thenReturn(List.of(MOUSE, KEYBOARD));
        when(productRepository.decreaseStock(anyLong(), anyInt())).thenReturn(true);
        when(orderRepository.insert(any())).thenAnswer(invocation -> invocation.<Order>getArgument(0).withId(7L));

        OrderDto created = orderService.create(new OrderDto(" Customer ", List.of(
                new OrderItemDto(2L, 1), new OrderItemDto(1L, 2), new OrderItemDto(2L, 3))));

        InOrder reservations = inOrder(productRepository);
        reservations.verify(productRepository).decreaseStock(1L, 2);
        reservations.verify(productRepository).decreaseStock(2L, 4);
        assertEquals("Customer", created.getCustomerName());
        assertEquals(OrderStatus.PENDING, created.getStatus());
        assertEquals(new BigDecimal("180.00"), created.getTotalAmount());
        assertEquals(3, created.getItems().size());
    }

    @Test
    void createFailsWithoutSavingWhenStockIsInsufficient() {
        when(productRepository.findAllByIds(any())).thenReturn(List.of(KEYBOARD));
        when(productRepository.decreaseStock(1L, 11)).thenReturn(false);

        assertThrows(InsufficientStockException.class,
                () -> orderService.create(new OrderDto("Customer", List.of(new OrderItemDto(1L, 11)))));
        verify(orderRepository, never()).insert(any());
    }

    @Test
    void cancelReleasesReservedStock() {
        when(orderRepository.findById(5L)).thenReturn(Optional.of(pendingOrder()));
        when(orderRepository.updateStatus(5L, Order.Status.PENDING, Order.Status.CANCELLED)).thenReturn(true);

        OrderDto cancelled = orderService.cancel(5L);

        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        verify(productRepository).increaseStock(1L, 2);
    }

    @Test
    void paidOrderCannotBeDeleted() {
        when(orderRepository.findById(5L)).thenReturn(Optional.of(pendingOrder().withStatus(Order.Status.PAID)));

        assertThrows(ConflictException.class, () -> orderService.delete(5L));
        verify(orderRepository, never()).delete(anyLong());
        verify(productRepository, never()).increaseStock(anyLong(), anyInt());
    }

    @Test
    void concurrentStatusChangeIsReportedAsConflict() {
        when(orderRepository.findById(5L)).thenReturn(Optional.of(pendingOrder()));
        when(orderRepository.updateStatus(5L, Order.Status.PENDING, Order.Status.PAID)).thenReturn(false);

        assertThrows(ConflictException.class, () -> orderService.pay(5L));
    }

    private Order pendingOrder() {
        return new Order(5L, "Customer", Order.Status.PENDING, new BigDecimal("100.00"),
                List.of(OrderItem.of(KEYBOARD, 2)));
    }
}
