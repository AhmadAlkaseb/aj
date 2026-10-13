package com.aj.products.controller;

import com.aj.products.dao.ProductDao;
import com.aj.products.dao.ProductDeletedDao;
import com.aj.products.entity.Product;
import com.aj.products.entity.ProductDeleted;
import io.javalin.Javalin;
import io.javalin.http.NotFoundResponse;

import java.time.Instant;
import java.util.List;

public class ProductDeletedController {

    private final ProductDao productDao;
    private final ProductDeletedDao productDeletedDao;

    public ProductDeletedController(ProductDao productDao, ProductDeletedDao productDeletedDao) {
        this.productDao = productDao;
        this.productDeletedDao = productDeletedDao;
    }

    public void registerRoutes(Javalin app) {
        app.get("/api/products/deleted", ctx -> {
            List<ProductDeleted> deletedProducts = productDeletedDao.findAll();
            ctx.json(deletedProducts);
        });

        app.get("/api/products/{productId}/deleted", ctx -> {
            Long productId = Long.parseLong(ctx.pathParam("productId"));
            productDao.findById(productId)
                    .orElseThrow(() -> new NotFoundResponse("Product not found"));

            List<ProductDeleted> deleted = productDeletedDao.findByProductId(productId);
            ctx.json(deleted);
        });

        app.post("/api/products/{productId}/deleted", ctx -> {
            Long productId = Long.parseLong(ctx.pathParam("productId"));
            Product product = productDao.findById(productId)
                    .orElseThrow(() -> new NotFoundResponse("Product not found"));

            ProductDeleted payload = ctx.bodyAsClass(ProductDeleted.class);
            payload.setProduct(product);
            if (payload.getDeletedAt() == null) {
                payload.setDeletedAt(Instant.now());
            }

            ProductDeleted saved = productDeletedDao.save(payload);
            ctx.status(201).json(saved);
        });
    }
}
