package com.mixamus.restapi.repository;

import com.mixamus.restapi.entity.Product;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, name, description, price, stock_quantity
            FROM products
            """;

    private static final RowMapper<Product> PRODUCT_ROW_MAPPER = (resultSet, rowNumber) -> {
        Product product = new Product();
        product.setId(resultSet.getLong("id"));
        product.setName(resultSet.getString("name"));
        product.setDescription(resultSet.getString("description"));
        product.setPrice(resultSet.getBigDecimal("price"));
        product.setStockQuantity(resultSet.getInt("stock_quantity"));
        return product;
    };

    private final JdbcTemplate jdbcTemplate;

    public List<Product> findAll() {
        return jdbcTemplate.query(SELECT_COLUMNS + " ORDER BY id", PRODUCT_ROW_MAPPER);
    }

    public Optional<Product> findById(Long id) {
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE id = ?", PRODUCT_ROW_MAPPER, id)
                .stream()
                .findFirst();
    }

    public Product save(Product product) {
        if (product.getId() == null) {
            insert(product);
        } else {
            jdbcTemplate.update("""
                            UPDATE products
                            SET name = ?, description = ?, price = ?, stock_quantity = ?
                            WHERE id = ?
                            """,
                    product.getName(),
                    product.getDescription(),
                    product.getPrice(),
                    product.getStockQuantity(),
                    product.getId());
        }
        return product;
    }

    public void delete(Product product) {
        jdbcTemplate.update("DELETE FROM products WHERE id = ?", product.getId());
    }

    private void insert(Product product) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO products (name, description, price, stock_quantity)
                            VALUES (?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, product.getName());
            statement.setString(2, product.getDescription());
            statement.setBigDecimal(3, product.getPrice());
            statement.setInt(4, product.getStockQuantity());
            return statement;
        }, keyHolder);

        if (keyHolder.getKey() == null) {
            throw new IllegalStateException("Database did not return the generated product ID");
        }
        product.setId(keyHolder.getKey().longValue());
    }
}
