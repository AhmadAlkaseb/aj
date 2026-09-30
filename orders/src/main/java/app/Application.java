package app;

import app.controller.OrderController;
import app.routes.OrderRoutes;
import io.javalin.Javalin;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import java.util.HashMap;
import java.util.Map;

public class Application {

    public static void main(String[] args) {
        EntityManagerFactory entityManagerFactory = createEntityManagerFactory();
        OrderController orderController = new OrderController(entityManagerFactory);

        Javalin app = Javalin.create();
        OrderRoutes.register(app, orderController);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (entityManagerFactory.isOpen()) {
                entityManagerFactory.close();
            }
        }));

        app.start(env("HOST", "0.0.0.0"), integerEnv("PORT", 7000));
    }

    private static EntityManagerFactory createEntityManagerFactory() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("jakarta.persistence.jdbc.url", env("DB_URL", "jdbc:postgresql://localhost:5433/orders"));
        properties.put("jakarta.persistence.jdbc.user", env("DB_USER", "postgres"));
        properties.put("jakarta.persistence.jdbc.password", env("DB_PASSWORD", "postgres"));
        properties.put("hibernate.hbm2ddl.auto", env("HIBERNATE_DDL_AUTO", "update"));
        properties.put("hibernate.show_sql", Boolean.parseBoolean(env("HIBERNATE_SHOW_SQL", "false")));

        return Persistence.createEntityManagerFactory("ordersPU", properties);
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static int integerEnv(String name, int defaultValue) {
        try {
            return Integer.parseInt(env(name, Integer.toString(defaultValue)));
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }
}
