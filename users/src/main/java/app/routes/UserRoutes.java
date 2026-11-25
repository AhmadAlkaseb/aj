package app.routes;
import app.controller.UserController;
import io.javalin.router.JavalinDefaultRoutingApi;
public final class UserRoutes {
    private UserRoutes() {}
    public static void register(JavalinDefaultRoutingApi app, UserController controller) { controller.register(app); }
}
