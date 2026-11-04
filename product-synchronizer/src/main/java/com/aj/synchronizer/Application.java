package com.aj.synchronizer;
import com.aj.common.*;
public class Application {
    public static void main(String[] args) {
        ProjectionClient owner=new ProjectionClient(Api.env("PRODUCT_VIEW_URL","http://localhost:7006"),
            Api.env("PROJECTION_API_TOKEN","aj-projection-local"));
        Bus bus=new Bus("aj.product-sync",owner::project);
        var app=Api.app("product-synchronizer"); Api.health(app,"product-synchronizer",() -> owner.healthy() && bus.healthy());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { app.stop(); bus.close(); })); Api.start(app,7007);
    }
}
