package com.mixamus.restapi.service;

import com.mixamus.restapi.entity.Order;
import com.mixamus.restapi.entity.OrderItem;
import com.mixamus.restapi.entity.Product;
import com.mixamus.restapi.exception.ConflictException;
import com.mixamus.restapi.exception.NotFoundException;
import com.mixamus.restapi.generated.model.OrderDto;
import com.mixamus.restapi.generated.model.OrderItemDto;
import com.mixamus.restapi.generated.model.OrderStatus;
import com.mixamus.restapi.repository.OrderFilter;
import com.mixamus.restapi.repository.OrderRepository;
import com.mixamus.restapi.repository.ProductRepository;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
        Page<Order> orders = orderRepository.findAllWithItems(filter, PageRequest.of(page, size));
        return new PageResult<>(orders.map(this::toDto).getContent(), orders.getTotalElements());
    }

    public OrderDto findById(Long id) {
        return toDto(findOrder(id));
    }

    /**
     * Товары блокируются (SELECT ... FOR UPDATE), остаток проверяется и уменьшается в сущности,
     * а UPDATE products и INSERT заказа с позициями Hibernate выполнит при коммите.
     * Любое исключение откатывает транзакцию целиком, вместе с уже списанным остатком.
     */
    @Transactional
    public OrderDto create(OrderDto request) {
        Map<Long, Integer> quantities = totalQuantityByProductId(request.getItems().stream()
                .map(item -> Map.entry(item.getProductId(), item.getQuantity()))
                .toList());
        Map<Long, Product> products = lockProducts(quantities);

        quantities.forEach((productId, quantity) -> products.get(productId).decreaseStock(quantity));

        Order order = new Order(request.getCustomerName().trim());
        for (OrderItemDto item : request.getItems()) {
            order.addItem(products.get(item.getProductId()), item.getQuantity());
        }
        return toDto(orderRepository.save(order));
    }

    /**
     * Параллельное изменение того же заказа отловит @Version (optimistic locking).
     */
    @Transactional
    public OrderDto pay(Long id) {
        Order order = findOrder(id);
        order.pay();
        return toDto(order);
    }

    @Transactional
    public OrderDto cancel(Long id) {
        Order order = findOrder(id);
        order.cancel();
        releaseStock(order);
        return toDto(order);
    }

    @Transactional
    public void delete(Long id) {
        Order order = findOrder(id);
        switch (order.getStatus()) {
            case PAID -> throw new ConflictException("Order " + id + " is PAID and cannot be deleted");
            case PENDING -> {
                order.cancel();
                releaseStock(order);
            }
            case CANCELLED -> {
                // Остаток уже возвращён при отмене
            }
        }
        orderRepository.delete(order);
    }

    private Order findOrder(Long id) {
        return orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new NotFoundException("Order with id " + id + " not found"));
    }

    private Map<Long, Product> lockProducts(Map<Long, Integer> quantities) {
        Map<Long, Product> products = productRepository.findAllByIdForUpdate(quantities.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        for (Long productId : quantities.keySet()) {
            if (!products.containsKey(productId)) {
                throw new NotFoundException("Product with id " + productId + " not found");
            }
        }
        return products;
    }

    /**
     * Если товар успели удалить, возвращать остаток некуда: такие позиции пропускаются.
     */
    private void releaseStock(Order order) {
        Map<Long, Integer> quantities = totalQuantityByProductId(order.getItems().stream()
                .map(item -> Map.entry(item.getProductId(), item.getQuantity()))
                .toList());
        productRepository.findAllByIdForUpdate(quantities.keySet())
                .forEach(product -> product.increaseStock(quantities.get(product.getId())));
    }

    /**
     * Один товар может встречаться в заказе несколько раз; TreeMap задаёт порядок блокировок.
     */
    private static Map<Long, Integer> totalQuantityByProductId(List<Map.Entry<Long, Integer>> items) {
        return items.stream().collect(Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue, Integer::sum, TreeMap::new));
    }

    private OrderDto toDto(Order order) {
        OrderDto dto = new OrderDto();
        dto.setId(order.getId());
        dto.setCustomerName(order.getCustomerName());
        dto.setStatus(OrderStatus.valueOf(order.getStatus().name()));
        dto.setTotalAmount(order.getTotalAmount());
        dto.setCreatedAt(order.getCreatedAt().atOffset(ZoneOffset.UTC));
        dto.setItems(order.getItems().stream().map(this::toDto).toList());
        return dto;
    }

    private OrderItemDto toDto(OrderItem item) {
        OrderItemDto dto = new OrderItemDto();
        dto.setProductId(item.getProductId());
        dto.setProductName(item.getProductName());
        dto.setQuantity(item.getQuantity());
        dto.setUnitPrice(item.getUnitPrice());
        dto.setTotalPrice(item.getTotalPrice());
        return dto;
    }
}
