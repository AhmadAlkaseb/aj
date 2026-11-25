package routes;

import controller.AuthController;
import io.javalin.Javalin;
import security.AccessTokenValidator;
import security.LoginStateStore;
import security.TokenRevocationStore;

public class AuthRoutes {
    public static void register(Javalin app) {
        TokenRevocationStore store = new TokenRevocationStore();
        LoginStateStore loginStateStore = new LoginStateStore();
        AccessTokenValidator jwt = new AccessTokenValidator(store);
        AuthController controller = new AuthController(store, loginStateStore, jwt);

        app.post(Routes.BASE_PATH + "/auth/tokens", controller::createToken);
        app.delete(Routes.BASE_PATH + "/auth/tokens/{tokenId}", controller::deleteToken);
        app.get(Routes.BASE_PATH + "/auth/authorize", controller::authorize);
        app.get(Routes.BASE_PATH + "/auth/callback", controller::callback);
        app.get(Routes.BASE_PATH + "/auth", controller::loginPage);

        // Alle domåne-endpoints kræver et gyldigt access token.
        app.beforeMatched(ctx -> {
            String path = ctx.matchedPath();
            if (path.equals(Routes.BASE_PATH + "/etl/runs")
                    || path.equals(Routes.BASE_PATH + "/books") || path.startsWith(Routes.BASE_PATH + "/books/")
                    || path.equals(Routes.BASE_PATH + "/authors") || path.startsWith(Routes.BASE_PATH + "/authors/")
                    || path.equals(Routes.BASE_PATH + "/categories") || path.startsWith(Routes.BASE_PATH + "/categories/")) {
                jwt.authenticate(ctx);
            }
        });
    }
}
