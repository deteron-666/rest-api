package com.mixamus.restapi.service;

import com.mixamus.restapi.generated.model.OrderDto;
import com.mixamus.restapi.generated.model.OrderItemDto;
import com.mixamus.restapi.entity.Order;
import com.mixamus.restapi.entity.OrderItem;
import com.mixamus.restapi.entity.Product;
import com.mixamus.restapi.repository.OrderRepository;
import com.mixamus.restapi.repository.ProductRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public List<OrderDto> findAll() {
        return orderRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    public OrderDto findById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        return toDto(order);
    }

    @Transactional
    public OrderDto create(OrderDto request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order must contain at least one item");
        }
        if (request.getCustomerName() == null || request.getCustomerName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer name is required");
        }

        Order order = new Order();
        order.setCustomerName(request.getCustomerName().trim());
        order.setStatus(Order.OrderStatus.PENDING);

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemDto itemRequest : request.getItems()) {
            if (itemRequest.getProductId() == null || itemRequest.getQuantity() == null || itemRequest.getQuantity() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each order item requires a valid product and quantity");
            }

            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Product with id " + itemRequest.getProductId() + " not found"));

            if (product.getStockQuantity() < itemRequest.getQuantity()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Not enough stock for product " + product.getName());
            }

            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setQuantity(itemRequest.getQuantity());
            item.setUnitPrice(product.getPrice());

            BigDecimal itemTotal = product.getPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity()));
            item.setTotalPrice(itemTotal);
            order.getItems().add(item);
            totalAmount = totalAmount.add(itemTotal);

            product.setStockQuantity(product.getStockQuantity() - itemRequest.getQuantity());
            productRepository.save(product);
        }

        order.setTotalAmount(totalAmount);
        return toDto(orderRepository.save(order));
    }

    @Transactional
    public void delete(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        orderRepository.delete(order);
    }

    private OrderDto toDto(Order order) {
        OrderDto dto = new OrderDto();
        dto.setId(order.getId());
        dto.setCustomerName(order.getCustomerName());
        dto.setStatus(order.getStatus().name());
        dto.setTotalAmount(order.getTotalAmount().doubleValue());
        dto.setItems(order.getItems().stream().map(this::toDto).toList());
        return dto;
    }

    private OrderItemDto toDto(OrderItem item) {
        OrderItemDto dto = new OrderItemDto();
        dto.setProductId(item.getProductId());
        dto.setProductName(item.getProductName());
        dto.setQuantity(item.getQuantity());
        dto.setUnitPrice(item.getUnitPrice().doubleValue());
        dto.setTotalPrice(item.getTotalPrice().doubleValue());
        return dto;
    }
}
