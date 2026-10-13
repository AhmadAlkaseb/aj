package com.aj.products.controller;

import com.aj.products.dao.ProductDao;
import com.aj.products.entity.Product;
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
            Long id = Long.parseLong(ctx.pathParam("id"));
            Product product = productDao.findById(id)
                    .orElseThrow(() -> new NotFoundResponse("Product not found"));
            ctx.json(product);
        });

        app.post("/api/products", ctx -> {
            Product request = ctx.bodyAsClass(Product.class);
            Product created = productDao.save(request);
            ctx.status(201).json(created);
        });

        app.put("/api/products/{id}", ctx -> {
            Long id = Long.parseLong(ctx.pathParam("id"));
            Product existing = productDao.findById(id)
                    .orElseThrow(() -> new NotFoundResponse("Product not found"));

            Product payload = ctx.bodyAsClass(Product.class);
            payload.setId(existing.getId());
            payload.setGuid(existing.getGuid());

            Product updated = productDao.update(payload);
            ctx.json(updated);
        });

        app.delete("/api/products/{id}", ctx -> {
            Long id = Long.parseLong(ctx.pathParam("id"));
            productDao.findById(id).orElseThrow(() -> new NotFoundResponse("Product not found"));
            productDao.delete(id);
            ctx.status(204);
        });
    }
}
