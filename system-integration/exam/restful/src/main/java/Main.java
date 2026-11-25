import io.javalin.Javalin;
import etl.DatabaseResourceManager;
import persistence.DatabaseSeeder;
import routes.Routes;
import routes.AuthRoutes;
import java.sql.SQLException;

public class Main {
    public static void main(String[] args) throws SQLException {
        Javalin app = Javalin.create();
        Routes.register(app);
        AuthRoutes.register(app);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            app.stop();
            DatabaseResourceManager.close();
        }));

        // Nulstil den normale database og indsæt 50 virkelige eksempler i hver tabel.
        DatabaseSeeder.seed();

        // Fjern // for at fordele data til shards og derefter samle dem i warehouse.
        new etl.WarehouseEtlService().run();

        app.start(7070);
    }
}
