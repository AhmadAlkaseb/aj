package app;
import app.controller.UserController;
import com.aj.common.*;
import persistence.entities.*;
public class Application {
    public static void main(String[] args) {
        Db db = new Db("users", 5433, User.class, UserDetails.class, UserDeleted.class);
        DemoSeeder.seed(db);
        var app = Api.app("users");
        var controller=new UserController(db); controller.register(app);
        Bus bus=new Bus(db,"aj.users",controller::event);
        Api.health(app,"users",() -> db.healthy() && bus.healthy());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { app.stop(); bus.close(); db.close(); }));
        Api.start(app,7002);
    }
}
