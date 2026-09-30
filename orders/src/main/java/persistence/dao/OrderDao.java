package persistence.dao;

import jakarta.persistence.EntityManager;
import persistence.entities.Order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class OrderDao {

    private final EntityManager entityManager;

    public OrderDao(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Order save(Order order) {
        if (order.getId() == null) {
            entityManager.persist(order);
            return order;
        }
        return entityManager.merge(order);
    }

    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(entityManager.find(Order.class, id));
    }

    public Optional<Order> findByGuid(UUID guid) {
        return entityManager.createQuery(
                        "select o from Order o where o.guid = :guid", Order.class)
                .setParameter("guid", guid)
                .getResultStream()
                .findFirst();
    }

    public List<Order> findAll() {
        return entityManager.createQuery("select o from Order o", Order.class)
                .getResultList();
    }

    public void deleteById(Long id) {
        findById(id).ifPresent(order -> entityManager.remove(order));
    }
}
