package com.example.libraryapi.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

@Configuration
public class DatabaseConfig {

    @Bean
    public DataSource dataSource(@Value("${spring.datasource.url}") String url) {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);

        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        dataSource.setUrl(url);
        return dataSource;
    }
}
