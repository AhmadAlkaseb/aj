package com.aj.products;

import com.aj.products.config.HibernateUtil;
import com.aj.products.controller.ProductController;
import com.aj.products.controller.ProductDeletedController;
import com.aj.products.controller.ProductDetailController;
import com.aj.products.dao.ProductDao;
import com.aj.products.dao.ProductDeletedDao;
import com.aj.products.dao.ProductDetailDao;
import io.javalin.Javalin;

public class ProductsApplication {

    public static void main(String[] args) {
        ProductDao productDao = new ProductDao(HibernateUtil.getSessionFactory());
        ProductDetailDao productDetailDao = new ProductDetailDao(HibernateUtil.getSessionFactory());
        ProductDeletedDao productDeletedDao = new ProductDeletedDao(HibernateUtil.getSessionFactory());

        Javalin app = Javalin.create();

        new ProductController(productDao).registerRoutes(app);
        new ProductDetailController(productDao, productDetailDao).registerRoutes(app);
        new ProductDeletedController(productDao, productDeletedDao).registerRoutes(app);

        int port = Integer.parseInt(System.getProperty("server.port", System.getenv().getOrDefault("PORT", "7000")));
        app.start(port);
    }
}
