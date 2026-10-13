package com.aj.products.dao;

import com.aj.products.entity.ProductDeleted;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.util.List;

public class ProductDeletedDao {

    private final SessionFactory sessionFactory;

    public ProductDeletedDao(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public ProductDeleted save(ProductDeleted productDeleted) {
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            session.persist(productDeleted);
            tx.commit();
            return productDeleted;
        }
    }

    public List<ProductDeleted> findAll() {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery("from ProductDeleted", ProductDeleted.class).list();
        }
    }

    public List<ProductDeleted> findByProductId(Long productId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery("from ProductDeleted where product.id = :productId", ProductDeleted.class)
                    .setParameter("productId", productId)
                    .list();
        }
    }
}
