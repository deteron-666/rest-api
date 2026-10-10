package com.mixamus.restapi.service;

import com.mixamus.restapi.entity.Order;
import com.mixamus.restapi.entity.OrderItem;
import com.mixamus.restapi.entity.Product;
import com.mixamus.restapi.exception.ConflictException;
import com.mixamus.restapi.exception.InsufficientStockException;
import com.mixamus.restapi.exception.NotFoundException;
import com.mixamus.restapi.generated.model.OrderDto;
import com.mixamus.restapi.generated.model.OrderItemDto;
import com.mixamus.restapi.generated.model.OrderStatus;
import com.mixamus.restapi.repository.OrderFilter;
import com.mixamus.restapi.repository.OrderRepository;
import com.mixamus.restapi.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Жизненный цикл заказа: PENDING -> PAID или PENDING -> CANCELLED.
 * Остаток товара резервируется при создании и возвращается при отмене или удалении PENDING-заказа.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public PageResult<OrderDto> findAll(OrderFilter filter, int page, int size) {
        return new PageResult<>(
                orderRepository.findAll(filter, (long) page * size, size).stream()
                        .map(this::toDto)
                        .toList(),
                orderRepository.count(filter));
    }

    public OrderDto findById(Long id) {
        return toDto(findOrder(id));
    }

    @Transactional
    public OrderDto create(OrderDto request) {
        Map<Long, Integer> quantities = totalQuantityByProductId(
                request.getItems().stream()
                        .map(item -> new OrderItemRequest(item.getProductId(), item.getQuantity()))
                        .toList());
        Map<Long, Product> products = loadProducts(quantities.keySet());

        reserveStock(quantities, products);

        List<OrderItem> items = request.getItems().stream()
                .map(item -> OrderItem.of(products.get(item.getProductId()), item.getQuantity()))
                .toList();
        BigDecimal totalAmount = items.stream()
                .map(OrderItem::totalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = new Order(null, request.getCustomerName().trim(), Order.Status.PENDING, totalAmount, items);
        return toDto(orderRepository.insert(order));
    }

    @Transactional
    public OrderDto pay(Long id) {
        return toDto(changeStatus(findOrder(id), Order.Status.PENDING, Order.Status.PAID));
    }

    @Transactional
    public OrderDto cancel(Long id) {
        Order cancelled = changeStatus(findOrder(id), Order.Status.PENDING, Order.Status.CANCELLED);
        releaseStock(cancelled);
        return toDto(cancelled);
    }

    @Transactional
    public void delete(Long id) {
        Order order = findOrder(id);
        switch (order.status()) {
            case PAID -> throw new ConflictException("Order " + id + " is PAID and cannot be deleted");
            case PENDING -> releaseStock(changeStatus(order, Order.Status.PENDING, Order.Status.CANCELLED));
            case CANCELLED -> {
                // Остаток уже возвращён при отмене
            }
        }
        orderRepository.delete(id);
    }

    private Order findOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order with id " + id + " not found"));
    }

    private Map<Long, Product> loadProducts(Collection<Long> productIds) {
        Map<Long, Product> products = productRepository.findAllByIds(productIds).stream()
                .collect(Collectors.toMap(Product::id, Function.identity()));
        for (Long productId : productIds) {
            if (!products.containsKey(productId)) {
                throw new NotFoundException("Product with id " + productId + " not found");
            }
        }
        return products;
    }

    /**
     * Списываем в порядке возрастания id товара: параллельные транзакции блокируют строки
     * в одном и том же порядке и не попадают в deadlock. При нехватке остатка исключение
     * откатывает транзакцию вместе с уже сделанными списаниями.
     */
    private void reserveStock(Map<Long, Integer> quantities, Map<Long, Product> products) {
        quantities.forEach((productId, quantity) -> {
            if (!productRepository.decreaseStock(productId, quantity)) {
                throw new InsufficientStockException(products.get(productId).name());
            }
        });
    }

    /**
     * Если товар успели удалить, возвращать остаток некуда, и строка просто не обновится.
     */
    private void releaseStock(Order order) {
        totalQuantityByProductId(order.items().stream()
                .map(item -> new OrderItemRequest(item.productId(), item.quantity()))
                .toList())
                .forEach(productRepository::increaseStock);
    }

    private Order changeStatus(Order order, Order.Status from, Order.Status to) {
        if (order.status() != from || !orderRepository.updateStatus(order.id(), from, to)) {
            throw new ConflictException("Order " + order.id() + " must be " + from + " to become " + to);
        }
        return order.withStatus(to);
    }

    /**
     * Один товар может встречаться в заказе несколько раз; TreeMap задаёт порядок блокировок.
     */
    private static Map<Long, Integer> totalQuantityByProductId(List<OrderItemRequest> items) {
        return items.stream().collect(Collectors.toMap(
                OrderItemRequest::productId, OrderItemRequest::quantity, Integer::sum, TreeMap::new));
    }

    private OrderDto toDto(Order order) {
        OrderDto dto = new OrderDto();
        dto.setId(order.id());
        dto.setCustomerName(order.customerName());
        dto.setStatus(OrderStatus.valueOf(order.status().name()));
        dto.setTotalAmount(order.totalAmount());
        dto.setItems(order.items().stream().map(this::toDto).toList());
        return dto;
    }

    private OrderItemDto toDto(OrderItem item) {
        OrderItemDto dto = new OrderItemDto();
        dto.setProductId(item.productId());
        dto.setProductName(item.productName());
        dto.setQuantity(item.quantity());
        dto.setUnitPrice(item.unitPrice());
        dto.setTotalPrice(item.totalPrice());
        return dto;
    }

    private record OrderItemRequest(Long productId, Integer quantity) {
    }
}
