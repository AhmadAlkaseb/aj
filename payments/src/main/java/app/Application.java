package app;
import app.controller.PaymentController;
import com.aj.common.*;
import persistence.entities.*;
public class Application {
    public static void main(String[] args) {
        Db db=new Db("payments",5436,Payment.class,PaymentDetails.class,PaymentDeleted.class);
        DemoSeeder.seed(db);
        var controller=new PaymentController(db); Bus bus=new Bus(db,"aj.payments",controller::event); var app=Api.app("payments"); controller.register(app);
        Api.health(app,"payments",() -> db.healthy() && bus.healthy());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { app.stop(); bus.close(); db.close(); })); Api.start(app,7005);
    }
}
