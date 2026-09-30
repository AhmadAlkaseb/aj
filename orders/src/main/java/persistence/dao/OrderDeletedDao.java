package persistence.dao;

import jakarta.persistence.EntityManager;
import persistence.entities.OrderDeleted;

import java.util.List;
import java.util.Optional;

public class OrderDeletedDao {

    private final EntityManager entityManager;

    public OrderDeletedDao(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public OrderDeleted save(OrderDeleted orderDeleted) {
        if (orderDeleted.getId() == null) {
            entityManager.persist(orderDeleted);
            return orderDeleted;
        }
        return entityManager.merge(orderDeleted);
    }

    public Optional<OrderDeleted> findById(Long id) {
        return Optional.ofNullable(entityManager.find(OrderDeleted.class, id));
    }

    public List<OrderDeleted> findByOrderId(Long orderId) {
        return entityManager.createQuery(
                        "select d from OrderDeleted d where d.order.id = :orderId",
                        OrderDeleted.class)
                .setParameter("orderId", orderId)
                .getResultList();
    }
}
