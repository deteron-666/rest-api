package com.mixamus.restapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mixamus.restapi.entity.Product;
import com.mixamus.restapi.exception.NotFoundException;
import com.mixamus.restapi.generated.model.ProductDto;
import com.mixamus.restapi.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void createTrimsNameAndNormalizesPriceScale() {
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProductDto created = productService.create(new ProductDto("  Keyboard ", new BigDecimal("79.5"), 3));

        assertEquals("Keyboard", created.getName());
        assertEquals(new BigDecimal("79.50"), created.getPrice());
    }

    @Test
    void updateChangesManagedEntityWithoutExplicitSave() {
        Product product = new Product("Keyboard", null, BigDecimal.ONE, 1);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        productService.update(1L, new ProductDto("Mouse", new BigDecimal("2"), 5));

        assertEquals("Mouse", product.getName());
        assertEquals(5, product.getStockQuantity());
        verify(productRepository, never()).save(any());
    }

    @Test
    void updateOfMissingProductThrowsNotFound() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> productService.update(1L, new ProductDto("Keyboard", BigDecimal.ONE, 1)));
    }

    @Test
    void deleteOfMissingProductThrowsNotFound() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> productService.delete(1L));
    }
}
