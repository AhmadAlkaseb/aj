package app.controller;

import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import persistence.dao.PaymentDao;
import persistence.entities.Payment;

public class PaymentController {

    private final EntityManagerFactory entityManagerFactory;

    public PaymentController(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    public void getAll(Context ctx) {
        try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
            ctx.json(new PaymentDao(entityManager).findAll());
        }
    }

    public void getById(Context ctx) {
        Long id = parseId(ctx);
        try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
            Payment payment = new PaymentDao(entityManager).findById(id)
                    .orElseThrow(() -> new NotFoundResponse("Payment not found"));
            ctx.json(payment);
        }
    }

    public void create(Context ctx) {
        Payment payment = ctx.bodyAsClass(Payment.class);
        payment.setId(null);

        try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
            entityManager.getTransaction().begin();
            try {
                new PaymentDao(entityManager).save(payment);
                entityManager.getTransaction().commit();
                ctx.status(201).json(payment);
            } catch (RuntimeException exception) {
                entityManager.getTransaction().rollback();
                throw exception;
            }
        }
    }

    public void update(Context ctx) {
        Long id = parseId(ctx);
        Payment payment = ctx.bodyAsClass(Payment.class);
        payment.setId(id);

        try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
            entityManager.getTransaction().begin();
            try {
                Payment updatedPayment = new PaymentDao(entityManager).save(payment);
                entityManager.getTransaction().commit();
                ctx.json(updatedPayment);
            } catch (RuntimeException exception) {
                entityManager.getTransaction().rollback();
                throw exception;
            }
        }
    }

    public void delete(Context ctx) {
        Long id = parseId(ctx);

        try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
            PaymentDao paymentDao = new PaymentDao(entityManager);
            if (paymentDao.findById(id).isEmpty()) {
                throw new NotFoundResponse("Payment not found");
            }

            entityManager.getTransaction().begin();
            try {
                paymentDao.deleteById(id);
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