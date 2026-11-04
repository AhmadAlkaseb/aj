package app;
import app.controller.OrderController;
import com.aj.common.*;
import persistence.entities.*;
public class Application {
    public static void main(String[] args) {
        Db db=new Db("orders",5435,Order.class,OrderDetails.class,OrderDeleted.class);
        DemoSeeder.seed(db);
        OrderController controller=new OrderController(db); controller.publishOwners(); Bus bus=new Bus(db,"aj.orders",controller::event);
        var maintenance=java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        maintenance.scheduleWithFixedDelay(() -> {try {controller.maintenance();} catch(Exception failure){org.slf4j.LoggerFactory.getLogger(Application.class).warn("Saga reconciliation will retry");}},10,10,java.util.concurrent.TimeUnit.SECONDS);
        var app=Api.app("orders"); controller.register(app); Api.health(app,"orders",() -> db.healthy() && bus.healthy());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { app.stop(); maintenance.shutdownNow(); bus.close(); db.close(); })); Api.start(app,7004);
    }
}
