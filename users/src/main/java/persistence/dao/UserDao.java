package persistence.dao;

import jakarta.persistence.EntityManager;
import persistence.entities.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class UserDao {

    private final EntityManager entityManager;

    public UserDao(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public User save(User user) {
        if (user.getId() == null) {
            entityManager.persist(user);
            return user;
        }
        return entityManager.merge(user);
    }

    public Optional<User> findById(Long id) {
        return Optional.ofNullable(entityManager.find(User.class, id));
    }

    public Optional<User> findByGuid(UUID guid) {
        return entityManager.createQuery(
                        "select user from User user where user.guid = :guid", User.class)
                .setParameter("guid", guid)
                .getResultStream()
                .findFirst();
    }

    public List<User> findAll() {
        return entityManager.createQuery("select user from User user", User.class)
                .getResultList();
    }

    public void deleteById(Long id) {
        findById(id).ifPresent(user -> entityManager.remove(user));
    }
}