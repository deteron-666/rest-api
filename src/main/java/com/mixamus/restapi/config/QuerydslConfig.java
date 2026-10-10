package com.mixamus.restapi.config;

import com.querydsl.sql.SQLQueryFactory;
import com.querydsl.sql.SQLTemplates;
import com.querydsl.sql.SQLTemplatesRegistry;
import com.querydsl.sql.spring.SpringConnectionProvider;
import com.querydsl.sql.spring.SpringExceptionTranslator;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuerydslConfig {

    /**
     * SQL-диалект (H2, PostgreSQL, ...) определяется по метаданным подключения,
     * поэтому одни и те же запросы работают и на H2 в dev, и на PostgreSQL в prod.
     */
    @Bean
    public com.querydsl.sql.Configuration querydslConfiguration(DataSource dataSource) throws SQLException {
        SQLTemplates templates;
        try (Connection connection = dataSource.getConnection()) {
            templates = new SQLTemplatesRegistry().getTemplates(connection.getMetaData());
        }
        com.querydsl.sql.Configuration configuration = new com.querydsl.sql.Configuration(templates);
        // SQLException -> иерархия DataAccessException Spring (DataIntegrityViolationException и т.д.)
        configuration.setExceptionTranslator(new SpringExceptionTranslator());
        return configuration;
    }

    /**
     * SpringConnectionProvider берёт соединение текущей Spring-транзакции,
     * поэтому запросы QueryDSL участвуют в @Transactional наравне с JdbcTemplate.
     * Вне транзакции он бросает исключение, поэтому все методы сервисов транзакционные.
     */
    @Bean
    public SQLQueryFactory sqlQueryFactory(com.querydsl.sql.Configuration configuration, DataSource dataSource) {
        return new SQLQueryFactory(configuration, new SpringConnectionProvider(dataSource));
    }
}
