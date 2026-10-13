package com.aj.products.dao;

import com.aj.products.entity.Product;
import com.aj.products.entity.ProductDetail;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.util.List;
import java.util.Optional;

public class ProductDetailDao {

    private final SessionFactory sessionFactory;

    public ProductDetailDao(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public ProductDetail save(ProductDetail productDetail) {
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            session.persist(productDetail);
            tx.commit();
            return productDetail;
        }
    }

    public Optional<ProductDetail> findByProductId(Long productId) {
        try (Session session = sessionFactory.openSession()) {
            ProductDetail detail = session.createQuery(
                            "from ProductDetail where product.id = :productId", ProductDetail.class)
                    .setParameter("productId", productId)
                    .uniqueResult();
            return Optional.ofNullable(detail);
        }
    }

    public List<ProductDetail> findAll() {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery("from ProductDetail", ProductDetail.class).list();
        }
    }

    public void deleteByProductId(Long productId) {
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            ProductDetail detail = session.createQuery(
                            "from ProductDetail where product.id = :productId", ProductDetail.class)
                    .setParameter("productId", productId)
                    .uniqueResult();
            if (detail != null) {
                session.remove(detail);
            }
            tx.commit();
        }
    }
}
