package app.routes;

import app.controller.OrderController;
import io.javalin.Javalin;

public final class OrderRoutes {

    private OrderRoutes() {
    }

    public static void register(Javalin app, OrderController orderController) {
        app.get("/api/orders", orderController::getAll);
        app.get("/api/orders/{id}", orderController::getById);
        app.post("/api/orders", orderController::create);
        app.put("/api/orders/{id}", orderController::update);
        app.delete("/api/orders/{id}", orderController::delete);
    }
}
