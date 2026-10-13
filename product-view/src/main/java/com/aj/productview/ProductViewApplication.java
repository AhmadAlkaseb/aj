package com.aj.productview;

import com.aj.productview.config.MongoConfig;
import com.aj.productview.controller.ProductController;
import com.aj.productview.dao.ProductDao;
import io.javalin.Javalin;

public class ProductViewApplication {

    public static void main(String[] args) {
        MongoConfig mongoConfig = new MongoConfig();
        ProductDao productDao = new ProductDao(mongoConfig.getDatabase());

        Javalin app = Javalin.create();
        new ProductController(productDao).registerRoutes(app);

        int port = Integer.parseInt(System.getProperty("server.port",
                System.getenv().getOrDefault("PORT", "7000")));
        app.start(port);
    }
}
