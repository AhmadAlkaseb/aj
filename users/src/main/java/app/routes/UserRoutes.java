package app.routes;
import app.controller.UserController;
import io.javalin.Javalin;
public final class UserRoutes {
    private UserRoutes() {}
    public static void register(Javalin app, UserController controller) { controller.register(app); }
}
