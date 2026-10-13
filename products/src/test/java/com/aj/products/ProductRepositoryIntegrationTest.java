package com.aj.products;

import com.aj.products.config.HibernateUtil;
import com.aj.products.dao.ProductDao;
import com.aj.products.entity.Product;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductRepositoryIntegrationTest {

    @BeforeEach
    void setUp() {
        System.setProperty("db.url", "jdbc:h2:mem:products_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        System.setProperty("db.user", "sa");
        System.setProperty("db.password", "");
        System.setProperty("db.driver", "org.h2.Driver");
        HibernateUtil.reset();
    }

    @AfterEach
    void tearDown() {
        HibernateUtil.reset();
        System.clearProperty("db.url");
        System.clearProperty("db.user");
        System.clearProperty("db.password");
        System.clearProperty("db.driver");
    }

    @Test
    void shouldPersistAndLoadProduct() {
        ProductDao productDao = new ProductDao(HibernateUtil.getSessionFactory());

        Product product = Product.builder()
                .guid(UUID.randomUUID())
                .build();

        Product saved = productDao.save(product);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getGuid()).isNotNull();
        assertThat(productDao.findById(saved.getId())).isPresent();
    }
}
