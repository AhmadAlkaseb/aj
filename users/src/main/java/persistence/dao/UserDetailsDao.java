package persistence.dao;

import jakarta.persistence.EntityManager;
import persistence.entities.UserDetails;

import java.util.Optional;

public class UserDetailsDao {

    private final EntityManager entityManager;

    public UserDetailsDao(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public UserDetails save(UserDetails userDetails) {
        if (userDetails.getUserId() == null) {
            entityManager.persist(userDetails);
            return userDetails;
        }
        return entityManager.merge(userDetails);
    }

    public Optional<UserDetails> findByUserId(Long userId) {
        return Optional.ofNullable(entityManager.find(UserDetails.class, userId));
    }

    public void deleteByUserId(Long userId) {
        findByUserId(userId).ifPresent(userDetails -> entityManager.remove(userDetails));
    }
}