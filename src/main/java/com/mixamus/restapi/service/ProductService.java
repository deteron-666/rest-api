package com.mixamus.restapi.service;

import com.mixamus.restapi.generated.model.ProductDto;
import com.mixamus.restapi.entity.Product;
import com.mixamus.restapi.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public List<ProductDto> findAll() {
        return productRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    public ProductDto findById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        return toDto(product);
    }

    @Transactional
    public ProductDto create(ProductDto request) {
        Product product = new Product();
        applyRequest(product, request);
        return toDto(productRepository.save(product));
    }

    @Transactional
    public ProductDto update(Long id, ProductDto request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        applyRequest(product, request);
        return toDto(productRepository.save(product));
    }

    @Transactional
    public void delete(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        productRepository.delete(product);
    }

    private void applyRequest(Product product, ProductDto request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product name is required");
        }
        if (request.getPrice() == null || request.getPrice() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product price must be non-negative");
        }
        if (request.getStockQuantity() == null || request.getStockQuantity() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stock quantity must be non-negative");
        }

        product.setName(request.getName().trim());
        product.setDescription(request.getDescription());
        product.setPrice(BigDecimal.valueOf(request.getPrice()));
        product.setStockQuantity(request.getStockQuantity());
    }

    private ProductDto toDto(Product product) {
        ProductDto dto = new ProductDto();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setPrice(product.getPrice().doubleValue());
        dto.setStockQuantity(product.getStockQuantity());
        return dto;
    }
}
