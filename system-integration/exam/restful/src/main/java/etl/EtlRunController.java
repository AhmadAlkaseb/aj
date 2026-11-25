package etl;

import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import java.util.Map;

public class EtlRunController {
    // Kaldes af POST /api/v1/etl/runs. JWT-kontrollen foretages inden controlleren.
    public void run(Context ctx) {
        try {
            // Starter hele processen og venter på resultatet, før HTTP-svaret sendes.
            var result = new WarehouseEtlService().run();
            // Returnerer antal rækker samt HATEOAS-links til de relaterede API-ressourcer.
            ctx.status(HttpStatus.OK).json(Map.of(
                    "runId", result.runId(), "shards", result.shards(),
                    "authors", result.authors(), "categories", result.categories(), "books", result.books(),
                    "_links", Map.of(
                            "books", controller.Hateoas.link(routes.Routes.BASE_PATH + "/books"),
                            "authors", controller.Hateoas.link(routes.Routes.BASE_PATH + "/authors"),
                            "categories", controller.Hateoas.link(routes.Routes.BASE_PATH + "/categories"))));
        } catch (java.sql.SQLException | RuntimeException exception) {
            // Et fejlet load rulles tilbage. Ingen delvis samling bliver synlig.
            ctx.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .json(Map.of("message", "ETL kunne ikke gennemføres; kontroller databaserne"));
            // Gemmer de tekniske fejldetaljer i serverens log, ikke i klientens svar.
            org.slf4j.LoggerFactory.getLogger(EtlRunController.class).error("ETL fejlede", exception);
        }
    }
}
