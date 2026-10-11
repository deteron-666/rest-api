package com.mixamus.restapi.service;

import com.mixamus.restapi.entity.Product;
import com.mixamus.restapi.exception.ConflictException;
import com.mixamus.restapi.exception.NotFoundException;
import com.mixamus.restapi.generated.model.ProductDto;
import com.mixamus.restapi.repository.ProductFilter;
import com.mixamus.restapi.repository.ProductPredicates;
import com.mixamus.restapi.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

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
        Page<Product> products = productRepository.findAll(
                ProductPredicates.matching(filter), PageRequest.of(page, size, Sort.by("id")));
        return new PageResult<>(products.map(this::toDto).getContent(), products.getTotalElements());
    }

    public ProductDto findById(Long id) {
        return toDto(findProduct(id));
    }

    @Transactional
    public ProductDto create(ProductDto request) {
        Product product = new Product(request.getName().trim(), request.getDescription(),
                money(request.getPrice()), request.getStockQuantity());
        return toDto(productRepository.save(product));
    }

    /**
     * Версия из запроса защищает от потерянного обновления между чтением товара клиентом и PUT:
     * если за это время товар изменили (например, заказ списал остаток), запрос отклоняется с 409.
     * Изменение между SELECT и UPDATE внутри этой транзакции отловит @Version сущности.
     * save() не нужен (dirty checking), а flush() — чтобы в ответе была уже новая версия.
     */
    @Transactional
    public ProductDto update(Long id, ProductDto request) {
        Product product = findProduct(id);
        if (request.getVersion() != null && !request.getVersion().equals(product.getVersion())) {
            throw new ConflictException("Product " + id + " was modified: expected version "
                    + request.getVersion() + ", current version " + product.getVersion());
        }
        product.update(request.getName().trim(), request.getDescription(),
                money(request.getPrice()), request.getStockQuantity());
        productRepository.flush();
        return toDto(product);
    }

    @Transactional
    public void delete(Long id) {
        productRepository.delete(findProduct(id));
    }

    private Product findProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product with id " + id + " not found"));
    }

    /**
     * Валидация гарантирует не больше 2 знаков после запятой, поэтому округление не нужно.
     */
    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private ProductDto toDto(Product product) {
        ProductDto dto = new ProductDto();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setPrice(product.getPrice());
        dto.setStockQuantity(product.getStockQuantity());
        dto.setVersion(product.getVersion());
        return dto;
    }
}
