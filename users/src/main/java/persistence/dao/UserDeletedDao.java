package persistence.dao;

import jakarta.persistence.EntityManager;
import persistence.entities.UserDeleted;

import java.util.List;
import java.util.Optional;

public class UserDeletedDao {

    private final EntityManager entityManager;

    public UserDeletedDao(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public UserDeleted save(UserDeleted userDeleted) {
        if (userDeleted.getId() == null) {
            entityManager.persist(userDeleted);
            return userDeleted;
        }
        return entityManager.merge(userDeleted);
    }

    public Optional<UserDeleted> findById(Long id) {
        return Optional.ofNullable(entityManager.find(UserDeleted.class, id));
    }

    public List<UserDeleted> findByUserId(Long userId) {
        return entityManager.createQuery(
                        "select deleted from UserDeleted deleted where deleted.user.id = :userId",
                        UserDeleted.class)
                .setParameter("userId", userId)
                .getResultList();
    }
}