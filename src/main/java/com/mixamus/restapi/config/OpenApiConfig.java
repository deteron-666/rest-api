package com.mixamus.restapi.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Order Management API",
        version = "1.0.0",
        description = "API for managing products and customer orders"
))
public class OpenApiConfig {
}
