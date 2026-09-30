package app.controller;

import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import persistence.dao.OrderDao;
import persistence.entities.Order;

public class OrderController {

    private final EntityManagerFactory entityManagerFactory;

    public OrderController(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    public void getAll(Context ctx) {
        try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
            ctx.json(new OrderDao(entityManager).findAll());
        }
    }

    public void getById(Context ctx) {
        Long id = parseId(ctx);
        try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
            Order order = new OrderDao(entityManager).findById(id)
                    .orElseThrow(() -> new NotFoundResponse("Order not found"));
            ctx.json(order);
        }
    }

    public void create(Context ctx) {
        Order order = ctx.bodyAsClass(Order.class);
        order.setId(null);

        try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
            entityManager.getTransaction().begin();
            try {
                new OrderDao(entityManager).save(order);
                entityManager.getTransaction().commit();
                ctx.status(201).json(order);
            } catch (RuntimeException exception) {
                entityManager.getTransaction().rollback();
                throw exception;
            }
        }
    }

    public void update(Context ctx) {
        Long id = parseId(ctx);
        Order order = ctx.bodyAsClass(Order.class);
        order.setId(id);

        try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
            entityManager.getTransaction().begin();
            try {
                Order updatedOrder = new OrderDao(entityManager).save(order);
                entityManager.getTransaction().commit();
                ctx.json(updatedOrder);
            } catch (RuntimeException exception) {
                entityManager.getTransaction().rollback();
                throw exception;
            }
        }
    }

    public void delete(Context ctx) {
        Long id = parseId(ctx);

        try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
            OrderDao orderDao = new OrderDao(entityManager);
            if (orderDao.findById(id).isEmpty()) {
                throw new NotFoundResponse("Order not found");
            }

            entityManager.getTransaction().begin();
            try {
                orderDao.deleteById(id);
                entityManager.getTransaction().commit();
                ctx.status(204);
            } catch (RuntimeException exception) {
                entityManager.getTransaction().rollback();
                throw exception;
            }
        }
    }

    private Long parseId(Context ctx) {
        try {
            return Long.parseLong(ctx.pathParam("id"));
        } catch (NumberFormatException exception) {
            throw new BadRequestResponse("Invalid id");
        }
    }
}
