package app.routes;

import app.controller.UserController;
import io.javalin.Javalin;

public final class UserRoutes {

    private UserRoutes() {
    }

    public static void register(Javalin app, UserController userController) {
        app.get("/api/users", userController::getAll);
        app.get("/api/users/{id}", userController::getById);
        app.post("/api/users", userController::create);
        app.put("/api/users/{id}", userController::update);
        app.delete("/api/users/{id}", userController::delete);
    }
}