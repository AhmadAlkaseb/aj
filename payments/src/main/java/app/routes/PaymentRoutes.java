package app.routes;
import app.controller.PaymentController;
import io.javalin.Javalin;
public final class PaymentRoutes {
    private PaymentRoutes() {}
    public static void register(Javalin app,PaymentController controller) { controller.register(app); }
}
