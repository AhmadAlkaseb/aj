package com.aj.products;
import com.aj.common.*;
import com.aj.products.controller.ProductController;
import com.aj.products.entity.*;
public class ProductsApplication {
    public static void main(String[] args) {
        Db db=new Db("products",5434,Product.class,ProductDetail.class,ProductDeleted.class);
        ProductController controller=new ProductController(db);
        DemoSeeder.seed(db, controller);
        Bus bus=new Bus(db,"aj.products",controller::event);
        var app=Api.app("products"); controller.registerRoutes(app); DescriptionDrafts.register(app);
        Api.health(app,"products",() -> db.healthy() && bus.healthy());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { app.stop(); bus.close(); db.close(); }));
        Api.start(app,7003);
    }
}
