package com.aj.common;

import jakarta.persistence.*;
import org.hibernate.cfg.Configuration;
import java.util.function.Function;

public final class Db implements AutoCloseable {
    private final EntityManagerFactory factory;
    public Db(String name, int localPort, Class<?>... entities) {
        Configuration c = new Configuration();
        c.setProperty("hibernate.connection.url", Api.env("DB_URL", "jdbc:postgresql://localhost:" + localPort + "/" + name));
        c.setProperty("hibernate.connection.username", Api.env("DB_USER", "postgres"));
        c.setProperty("hibernate.connection.password", Api.env("DB_PASSWORD", "postgres"));
        org.flywaydb.core.Flyway.configure().dataSource(
            Api.env("DB_URL","jdbc:postgresql://localhost:"+localPort+"/"+name),
            Api.env("DB_USER","postgres"),Api.env("DB_PASSWORD","postgres"))
            .locations("classpath:db/migration/"+name).baselineVersion("1")
            .baselineOnMigrate(Boolean.parseBoolean(Api.env("DB_BASELINE_EXISTING","false")))
            .cleanDisabled(true).load().migrate();
        c.setProperty("hibernate.hbm2ddl.auto", "validate");
        c.setProperty("hibernate.jdbc.time_zone", "UTC");
        c.setProperty("hibernate.connection.pool_size", "12");
        c.setProperty("hibernate.connection.autocommit", "false");
        c.setProperty("hibernate.connection.driver_class", "org.postgresql.Driver");
        c.setProperty("hibernate.connection.isolation", "2");
        c.setProperty("hibernate.connection.connectTimeout", "3");
        c.setProperty("hibernate.connection.socketTimeout", "4");
        c.addAnnotatedClass(Outbox.class); c.addAnnotatedClass(Inbox.class);
        for (Class<?> entity : entities) c.addAnnotatedClass(entity);
        factory = c.buildSessionFactory();
    }
    public <T> T tx(Function<EntityManager, T> fn) {
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            try { T result = fn.apply(em); em.getTransaction().commit(); return result; }
            catch (RuntimeException e) { if (em.getTransaction().isActive()) em.getTransaction().rollback(); throw e; }
        }
    }
    public boolean healthy() { return tx(em -> ((Number)em.createNativeQuery("select 1").getSingleResult()).intValue() == 1); }
    public void close() { factory.close(); }
}
