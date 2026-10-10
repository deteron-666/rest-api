package com.mixamus.restapi.controller;

import com.mixamus.restapi.entity.Order;
import com.mixamus.restapi.generated.api.OrdersApi;
import com.mixamus.restapi.generated.model.OrderDto;
import com.mixamus.restapi.generated.model.OrderStatus;
import com.mixamus.restapi.repository.OrderFilter;
import com.mixamus.restapi.service.OrderService;
import com.mixamus.restapi.service.PageResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OrderController implements OrdersApi {

    private final OrderService orderService;

    @Override
    public ResponseEntity<List<OrderDto>> getOrders(String customerName, OrderStatus status,
                                                    Integer page, Integer size) {
        Order.Status domainStatus = status == null ? null : Order.Status.valueOf(status.name());
        PageResult<OrderDto> result = orderService.findAll(new OrderFilter(customerName, domainStatus), page, size);
        return ResponseEntity.ok()
                .header(Headers.TOTAL_COUNT, String.valueOf(result.total()))
                .body(result.items());
    }

    @Override
    public ResponseEntity<OrderDto> getOrderById(Long orderId) {
        return ResponseEntity.ok(orderService.findById(orderId));
    }

    @Override
    public ResponseEntity<OrderDto> createOrder(OrderDto orderDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(orderDto));
    }

    @Override
    public ResponseEntity<OrderDto> payOrder(Long orderId) {
        return ResponseEntity.ok(orderService.pay(orderId));
    }

    @Override
    public ResponseEntity<OrderDto> cancelOrder(Long orderId) {
        return ResponseEntity.ok(orderService.cancel(orderId));
    }

    @Override
    public ResponseEntity<Void> deleteOrder(Long orderId) {
        orderService.delete(orderId);
        return ResponseEntity.noContent().build();
    }
}
