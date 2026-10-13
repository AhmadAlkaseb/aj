package com.aj.productview.dao;

import com.aj.productview.entity.Product;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ProductDao {

    private final MongoCollection<Product> collection;

    public ProductDao(MongoDatabase database) {
        this.collection = database.getCollection("products", Product.class);
    }

    public Product save(Product product) {
        if (product.getId() == null || product.getId().isBlank()) {
            product.setId(UUID.randomUUID().toString());
        }

        Instant now = Instant.now();
        product.setCreatedAt(now);
        product.setUpdatedAt(now);

        collection.insertOne(product);
        return product;
    }

    public List<Product> findAll() {
        return collection.find().into(new ArrayList<>());
    }

    public Optional<Product> findById(String id) {
        Product product = collection.find(Filters.eq("_id", id)).first();
        return Optional.ofNullable(product);
    }

    public Optional<Product> update(String id, Product product) {
        Product existing = findById(id).orElse(null);
        if (existing == null) {
            return Optional.empty();
        }

        product.setId(id);
        product.setCreatedAt(existing.getCreatedAt());
        product.setUpdatedAt(Instant.now());

        collection.replaceOne(Filters.eq("_id", id), product);
        return Optional.of(product);
    }

    public boolean delete(String id) {
        return collection.deleteOne(Filters.eq("_id", id)).getDeletedCount() > 0;
    }
}
