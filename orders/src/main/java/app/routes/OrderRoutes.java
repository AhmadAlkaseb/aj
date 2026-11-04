package app.routes;
import app.controller.OrderController;
import io.javalin.Javalin;
public final class OrderRoutes {
    private OrderRoutes() {}
    public static void register(Javalin app,OrderController controller) { controller.register(app); }
}
