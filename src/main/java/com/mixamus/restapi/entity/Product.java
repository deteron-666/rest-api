package com.mixamus.restapi.entity;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    private Long id;

    private String name;

    private String description;

    private BigDecimal price;

    private Integer stockQuantity = 0;
}
