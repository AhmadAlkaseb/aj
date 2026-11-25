package app;
import app.controller.UserController;
import com.aj.common.*;
import persistence.entities.*;
public class Application {
    public static void main(String[] args) {
        Db db = new Db("users", 5433, User.class, UserDetails.class, UserDeleted.class);
        DemoSeeder.seed(db);
        var controller=new UserController(db);
        Bus bus=new Bus(db,"aj.users",controller::event);
        var app = Api.app("users", c -> {
            controller.register(c.routes);
            Api.health(c.routes,"users",() -> db.healthy() && bus.healthy());
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { app.stop(); bus.close(); db.close(); }));
        Api.start(app,7002);
    }
}
