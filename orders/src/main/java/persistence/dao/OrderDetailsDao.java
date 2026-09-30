package persistence.dao;

import jakarta.persistence.EntityManager;
import persistence.entities.OrderDetails;

import java.util.Optional;

public class OrderDetailsDao {

    private final EntityManager entityManager;

    public OrderDetailsDao(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public OrderDetails save(OrderDetails orderDetails) {
        if (orderDetails.getOrderId() == null) {
            entityManager.persist(orderDetails);
            return orderDetails;
        }
        return entityManager.merge(orderDetails);
    }

    public Optional<OrderDetails> findByOrderId(Long orderId) {
        return Optional.ofNullable(entityManager.find(OrderDetails.class, orderId));
    }

    public void deleteByOrderId(Long orderId) {
        findByOrderId(orderId).ifPresent(orderDetails -> entityManager.remove(orderDetails));
    }
}
