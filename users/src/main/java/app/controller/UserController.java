package app.controller;

import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import persistence.dao.UserDao;
import persistence.entities.User;

public class UserController {

    private final EntityManagerFactory entityManagerFactory;

    public UserController(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    public void getAll(Context ctx) {
        try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
            ctx.json(new UserDao(entityManager).findAll());
        }
    }

    public void getById(Context ctx) {
        Long id = parseId(ctx);
        try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
            User user = new UserDao(entityManager).findById(id)
                    .orElseThrow(() -> new NotFoundResponse("User not found"));
            ctx.json(user);
        }
    }

    public void create(Context ctx) {
        User user = ctx.bodyAsClass(User.class);
        user.setId(null);

        try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
            entityManager.getTransaction().begin();
            try {
                new UserDao(entityManager).save(user);
                entityManager.getTransaction().commit();
                ctx.status(201).json(user);
            } catch (RuntimeException exception) {
                entityManager.getTransaction().rollback();
                throw exception;
            }
        }
    }

    public void update(Context ctx) {
        Long id = parseId(ctx);
        User user = ctx.bodyAsClass(User.class);
        user.setId(id);

        try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
            entityManager.getTransaction().begin();
            try {
                User updatedUser = new UserDao(entityManager).save(user);
                entityManager.getTransaction().commit();
                ctx.json(updatedUser);
            } catch (RuntimeException exception) {
                entityManager.getTransaction().rollback();
                throw exception;
            }
        }
    }

    public void delete(Context ctx) {
        Long id = parseId(ctx);

        try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
            UserDao userDao = new UserDao(entityManager);
            if (userDao.findById(id).isEmpty()) {
                throw new NotFoundResponse("User not found");
            }

            entityManager.getTransaction().begin();
            try {
                userDao.deleteById(id);
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