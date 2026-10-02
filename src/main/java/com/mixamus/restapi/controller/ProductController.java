package com.mixamus.restapi.controller;

import com.mixamus.restapi.generated.model.ProductDto;
import com.mixamus.restapi.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product catalog operations")
public class ProductController {

    private final ProductService productService;

    @GetMapping("/products")
    @Operation(summary = "Get all products")
    public ResponseEntity<List<ProductDto>> getProducts() {
        return ResponseEntity.ok(productService.findAll());
    }

    @GetMapping("/products/{productId}")
    @Operation(summary = "Get a product by ID")
    public ResponseEntity<ProductDto> getProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(productService.findById(productId));
    }

    @PostMapping("/products")
    @Operation(summary = "Create a product")
    public ResponseEntity<ProductDto> createProduct(@Valid @RequestBody ProductDto request) {
        ProductDto created = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/products/{productId}")
    @Operation(summary = "Update a product")
    public ResponseEntity<ProductDto> updateProduct(@PathVariable Long productId,
                                                  @Valid @RequestBody ProductDto request) {
        return ResponseEntity.ok(productService.update(productId, request));
    }

    @DeleteMapping("/products/{productId}")
    @Operation(summary = "Delete a product")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long productId) {
        productService.delete(productId);
        return ResponseEntity.noContent().build();
    }
}
