package com.mixamus.restapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.mixamus.restapi.entity.Product;
import com.mixamus.restapi.exception.NotFoundException;
import com.mixamus.restapi.generated.model.ProductDto;
import com.mixamus.restapi.repository.ProductRepository;
import java.math.BigDecimal;
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
        when(productRepository.insert(any())).thenAnswer(invocation -> invocation.<Product>getArgument(0).withId(1L));

        ProductDto created = productService.create(new ProductDto("  Keyboard ", new BigDecimal("79.5"), 3));

        assertEquals("Keyboard", created.getName());
        assertEquals(new BigDecimal("79.50"), created.getPrice());
    }

    @Test
    void updateOfMissingProductThrowsNotFound() {
        when(productRepository.update(any())).thenReturn(false);

        assertThrows(NotFoundException.class,
                () -> productService.update(1L, new ProductDto("Keyboard", BigDecimal.ONE, 1)));
    }

    @Test
    void deleteOfMissingProductThrowsNotFound() {
        when(productRepository.delete(1L)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> productService.delete(1L));
    }
}
