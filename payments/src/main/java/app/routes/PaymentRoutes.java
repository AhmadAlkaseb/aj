package app.routes;
import app.controller.PaymentController;
import io.javalin.router.JavalinDefaultRoutingApi;
public final class PaymentRoutes {
    private PaymentRoutes() {}
    public static void register(JavalinDefaultRoutingApi app,PaymentController controller) { controller.register(app); }
}
