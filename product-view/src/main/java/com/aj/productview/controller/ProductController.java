package com.aj.productview.controller;

import com.aj.productview.dao.ProductDao;
import com.aj.productview.entity.Product;
import io.javalin.Javalin;
import io.javalin.http.NotFoundResponse;

import java.util.List;

public class ProductController {

    private final ProductDao productDao;

    public ProductController(ProductDao productDao) {
        this.productDao = productDao;
    }

    public void registerRoutes(Javalin app) {
        app.get("/api/products", ctx -> {
            List<Product> products = productDao.findAll();
            ctx.json(products);
        });

        app.get("/api/products/{id}", ctx -> {
            String id = ctx.pathParam("id");
            Product product = productDao.findById(id)
                    .orElseThrow(() -> new NotFoundResponse("Product not found"));
            ctx.json(product);
        });

        app.post("/api/products", ctx -> {
            Product payload = ctx.bodyAsClass(Product.class);
            Product created = productDao.save(payload);
            ctx.status(201).json(created);
        });

        app.put("/api/products/{id}", ctx -> {
            String id = ctx.pathParam("id");
            Product payload = ctx.bodyAsClass(Product.class);
            Product updated = productDao.update(id, payload)
                    .orElseThrow(() -> new NotFoundResponse("Product not found"));
            ctx.json(updated);
        });

        app.delete("/api/products/{id}", ctx -> {
            String id = ctx.pathParam("id");
            boolean deleted = productDao.delete(id);
            if (!deleted) {
                throw new NotFoundResponse("Product not found");
            }
            ctx.status(204);
        });
    }
}
