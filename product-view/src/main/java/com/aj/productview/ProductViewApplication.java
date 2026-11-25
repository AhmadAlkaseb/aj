package com.aj.productview;
import com.aj.common.*;
import com.aj.productview.persistence.MongoStore;
import com.aj.productview.api.InternalProjectionApi;
import io.javalin.http.BadRequestResponse;
import java.util.Set;
public class ProductViewApplication {
    public static void main(String[] args) {
        MongoStore store=new MongoStore();
        var app=Api.app("product-view",c -> {
            com.aj.productview.api.CatalogueGraphql.register(c.routes,store);
            InternalProjectionApi.register(c.routes,Api.env("PROJECTION_API_TOKEN","aj-projection-local"),store::project,store::healthy);
            c.routes.get("/api/v1/products",ctx -> { String status=ctx.queryParam("status");
                if(status!=null && !Set.of("AVAILABLE","UNAVAILABLE","RESERVED","SOLD").contains(status)) throw new BadRequestResponse("Ugyldig status"); ctx.json(store.list(status)); });
            c.routes.get("/api/v1/products/{productGuid}",ctx -> ctx.json(store.get(Api.guid(ctx.pathParam("productGuid")))));
            Api.health(c.routes,"product-view",store::healthy);
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { app.stop(); store.close(); })); Api.start(app,7006);
    }
}
