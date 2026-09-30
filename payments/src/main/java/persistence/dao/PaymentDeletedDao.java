package persistence.dao;

import jakarta.persistence.EntityManager;
import persistence.entities.PaymentDeleted;

import java.util.List;
import java.util.Optional;

public class PaymentDeletedDao {

    private final EntityManager entityManager;

    public PaymentDeletedDao(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public PaymentDeleted save(PaymentDeleted paymentDeleted) {
        if (paymentDeleted.getId() == null) {
            entityManager.persist(paymentDeleted);
            return paymentDeleted;
        }
        return entityManager.merge(paymentDeleted);
    }

    public Optional<PaymentDeleted> findById(Long id) {
        return Optional.ofNullable(entityManager.find(PaymentDeleted.class, id));
    }

    public List<PaymentDeleted> findByPaymentId(Long paymentId) {
        return entityManager.createQuery(
                        "select d from PaymentDeleted d where d.payment.id = :paymentId",
                        PaymentDeleted.class)
                .setParameter("paymentId", paymentId)
                .getResultList();
    }
}