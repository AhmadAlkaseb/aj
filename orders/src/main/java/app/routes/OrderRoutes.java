package app.routes;
import app.controller.OrderController;
import io.javalin.router.JavalinDefaultRoutingApi;
public final class OrderRoutes {
    private OrderRoutes() {}
    public static void register(JavalinDefaultRoutingApi app,OrderController controller) { controller.register(app); }
}
