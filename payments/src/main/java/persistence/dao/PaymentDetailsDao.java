package persistence.dao;

import jakarta.persistence.EntityManager;
import persistence.entities.PaymentDetails;

import java.util.Optional;

public class PaymentDetailsDao {

    private final EntityManager entityManager;

    public PaymentDetailsDao(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public PaymentDetails save(PaymentDetails paymentDetails) {
        if (paymentDetails.getPaymentId() == null) {
            entityManager.persist(paymentDetails);
            return paymentDetails;
        }
        return entityManager.merge(paymentDetails);
    }

    public Optional<PaymentDetails> findByPaymentId(Long paymentId) {
        return Optional.ofNullable(entityManager.find(PaymentDetails.class, paymentId));
    }

    public void deleteByPaymentId(Long paymentId) {
        findByPaymentId(paymentId).ifPresent(paymentDetails -> entityManager.remove(paymentDetails));
    }
}