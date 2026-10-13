package com.aj.products.controller;

import com.aj.products.dao.ProductDao;
import com.aj.products.dao.ProductDetailDao;
import com.aj.products.entity.Product;
import com.aj.products.entity.ProductDetail;
import io.javalin.Javalin;
import io.javalin.http.NotFoundResponse;

public class ProductDetailController {

    private final ProductDao productDao;
    private final ProductDetailDao productDetailDao;

    public ProductDetailController(ProductDao productDao, ProductDetailDao productDetailDao) {
        this.productDao = productDao;
        this.productDetailDao = productDetailDao;
    }

    public void registerRoutes(Javalin app) {
        app.get("/api/products/{productId}/details", ctx -> {
            Long productId = Long.parseLong(ctx.pathParam("productId"));
            Product product = productDao.findById(productId)
                    .orElseThrow(() -> new NotFoundResponse("Product not found"));

            ProductDetail detail = productDetailDao.findByProductId(product.getId())
                    .orElseThrow(() -> new NotFoundResponse("Product details not found"));
            ctx.json(detail);
        });

        app.post("/api/products/{productId}/details", ctx -> {
            Long productId = Long.parseLong(ctx.pathParam("productId"));
            Product product = productDao.findById(productId)
                    .orElseThrow(() -> new NotFoundResponse("Product not found"));

            ProductDetail payload = ctx.bodyAsClass(ProductDetail.class);
            payload.setProduct(product);
            payload.setProductId(product.getId());

            ProductDetail saved = productDetailDao.save(payload);
            ctx.status(201).json(saved);
        });

        app.delete("/api/products/{productId}/details", ctx -> {
            Long productId = Long.parseLong(ctx.pathParam("productId"));
            productDao.findById(productId)
                    .orElseThrow(() -> new NotFoundResponse("Product not found"));
            productDetailDao.deleteByProductId(productId);
            ctx.status(204);
        });
    }
}
