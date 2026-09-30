package app.routes;

import app.controller.PaymentController;
import io.javalin.Javalin;

public final class PaymentRoutes {

    private PaymentRoutes() {
    }

    public static void register(Javalin app, PaymentController paymentController) {
        app.get("/api/payments", paymentController::getAll);
        app.get("/api/payments/{id}", paymentController::getById);
        app.post("/api/payments", paymentController::create);
        app.put("/api/payments/{id}", paymentController::update);
        app.delete("/api/payments/{id}", paymentController::delete);
    }
}