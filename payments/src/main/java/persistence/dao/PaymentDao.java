package persistence.dao;

import jakarta.persistence.EntityManager;
import persistence.entities.Payment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PaymentDao {

    private final EntityManager entityManager;

    public PaymentDao(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Payment save(Payment payment) {
        if (payment.getId() == null) {
            entityManager.persist(payment);
            return payment;
        }
        return entityManager.merge(payment);
    }

    public Optional<Payment> findById(Long id) {
        return Optional.ofNullable(entityManager.find(Payment.class, id));
    }

    public Optional<Payment> findByGuid(UUID guid) {
        return entityManager.createQuery(
                        "select p from Payment p where p.guid = :guid", Payment.class)
                .setParameter("guid", guid)
                .getResultStream()
                .findFirst();
    }

    public List<Payment> findAll() {
        return entityManager.createQuery("select p from Payment p", Payment.class)
                .getResultList();
    }

    public void deleteById(Long id) {
        findById(id).ifPresent(payment -> entityManager.remove(payment));
    }
}