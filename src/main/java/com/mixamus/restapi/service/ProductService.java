package com.mixamus.restapi.service;

import com.mixamus.restapi.entity.Product;
import com.mixamus.restapi.exception.NotFoundException;
import com.mixamus.restapi.generated.model.ProductDto;
import com.mixamus.restapi.repository.ProductFilter;
import com.mixamus.restapi.repository.ProductRepository;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Формат полей (обязательность, длины, диапазоны, 2 знака в цене) проверяется
 * Bean Validation по аннотациям DTO, сгенерированным из openapi.yaml.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    public PageResult<ProductDto> findAll(ProductFilter filter, int page, int size) {
        return new PageResult<>(
                productRepository.findAll(filter, (long) page * size, size).stream()
                        .map(this::toDto)
                        .toList(),
                productRepository.count(filter));
    }

    public ProductDto findById(Long id) {
        return productRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> notFound(id));
    }

    @Transactional
    public ProductDto create(ProductDto request) {
        return toDto(productRepository.insert(toEntity(null, request)));
    }

    @Transactional
    public ProductDto update(Long id, ProductDto request) {
        Product product = toEntity(id, request);
        if (!productRepository.update(product)) {
            throw notFound(id);
        }
        return toDto(product);
    }

    @Transactional
    public void delete(Long id) {
        if (!productRepository.delete(id)) {
            throw notFound(id);
        }
    }

    private Product toEntity(Long id, ProductDto request) {
        return new Product(
                id,
                request.getName().trim(),
                request.getDescription(),
                // Валидация гарантирует не больше 2 знаков после запятой, поэтому округление не нужно
                request.getPrice().setScale(2, RoundingMode.UNNECESSARY),
                request.getStockQuantity());
    }

    private ProductDto toDto(Product product) {
        ProductDto dto = new ProductDto();
        dto.setId(product.id());
        dto.setName(product.name());
        dto.setDescription(product.description());
        dto.setPrice(product.price());
        dto.setStockQuantity(product.stockQuantity());
        return dto;
    }

    private NotFoundException notFound(Long id) {
        return new NotFoundException("Product with id " + id + " not found");
    }
}
