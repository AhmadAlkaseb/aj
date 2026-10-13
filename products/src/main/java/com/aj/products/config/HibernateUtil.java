package com.aj.products.config;

import com.aj.products.entity.Product;
import com.aj.products.entity.ProductDeleted;
import com.aj.products.entity.ProductDetail;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;

import java.util.Properties;

public final class HibernateUtil {

    private static SessionFactory sessionFactory;

    private HibernateUtil() {
    }

    public static synchronized SessionFactory getSessionFactory() {
        if (sessionFactory == null) {
            sessionFactory = buildSessionFactory();
        }
        return sessionFactory;
    }

    public static synchronized void reset() {
        if (sessionFactory != null && sessionFactory.isOpen()) {
            sessionFactory.close();
        }
        sessionFactory = null;
    }

    public static SessionFactory buildSessionFactory() {
        String url = System.getProperty("db.url", System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/products_db"));
        String user = System.getProperty("db.user", System.getenv().getOrDefault("DB_USER", "postgres"));
        String password = System.getProperty("db.password", System.getenv().getOrDefault("DB_PASSWORD", "postgres"));
        String driver = System.getProperty("db.driver", System.getenv().getOrDefault("DB_DRIVER", "org.postgresql.Driver"));

        boolean h2 = url.startsWith("jdbc:h2");

        Properties properties = new Properties();
        properties.setProperty("hibernate.connection.driver_class", driver);
        properties.setProperty("hibernate.connection.url", url);
        properties.setProperty("hibernate.connection.username", user);
        properties.setProperty("hibernate.connection.password", password);
        properties.setProperty("hibernate.dialect", h2 ? "org.hibernate.dialect.H2Dialect" : "org.hibernate.dialect.PostgreSQLDialect");
        properties.setProperty("hibernate.hbm2ddl.auto", h2 ? "create-drop" : "update");
        properties.setProperty("hibernate.show_sql", "false");
        properties.setProperty("hibernate.format_sql", "true");
        properties.setProperty("hibernate.default_schema", "public");
        properties.setProperty("hibernate.jdbc.time_zone", "UTC");

        Configuration configuration = new Configuration();
        configuration.setProperties(properties);
        configuration.addAnnotatedClass(Product.class);
        configuration.addAnnotatedClass(ProductDetail.class);
        configuration.addAnnotatedClass(ProductDeleted.class);

        StandardServiceRegistryBuilder registryBuilder = new StandardServiceRegistryBuilder();
        registryBuilder.applySettings(properties);

        return configuration.buildSessionFactory(registryBuilder.build());
    }
}
