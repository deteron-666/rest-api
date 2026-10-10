package com.mixamus.restapi.controller;

import com.mixamus.restapi.generated.api.ProductsApi;
import com.mixamus.restapi.generated.model.ProductDto;
import com.mixamus.restapi.repository.ProductFilter;
import com.mixamus.restapi.service.PageResult;
import com.mixamus.restapi.service.ProductService;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Маршруты, параметры, валидация и описание для Swagger берутся из интерфейса ProductsApi,
 * сгенерированного из openapi.yaml, поэтому спецификация и реализация не могут разойтись.
 */
@RestController
@RequiredArgsConstructor
public class ProductController implements ProductsApi {

    private final ProductService productService;

    @Override
    public ResponseEntity<List<ProductDto>> getProducts(String name, BigDecimal minPrice, BigDecimal maxPrice,
                                                        Boolean inStock, Integer page, Integer size) {
        PageResult<ProductDto> result = productService.findAll(
                new ProductFilter(name, minPrice, maxPrice, inStock), page, size);
        return ResponseEntity.ok()
                .header(Headers.TOTAL_COUNT, String.valueOf(result.total()))
                .body(result.items());
    }

    @Override
    public ResponseEntity<ProductDto> getProductById(Long productId) {
        return ResponseEntity.ok(productService.findById(productId));
    }

    @Override
    public ResponseEntity<ProductDto> createProduct(ProductDto productDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(productDto));
    }

    @Override
    public ResponseEntity<ProductDto> updateProduct(Long productId, ProductDto productDto) {
        return ResponseEntity.ok(productService.update(productId, productDto));
    }

    @Override
    public ResponseEntity<Void> deleteProduct(Long productId) {
        productService.delete(productId);
        return ResponseEntity.noContent().build();
    }
}
