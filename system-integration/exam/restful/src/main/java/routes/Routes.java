package routes;

import controller.AuthorController;
import controller.BookController;
import controller.CategoryController;
import io.javalin.Javalin;

public class Routes {
    public static final String BASE_PATH = "/api/v1";

    public static void register(Javalin app) {
        // Aktiverer fælles sikkerhedsheaders og CSRF-kontrol før vores endpoints håndteres.
        security.HttpSecurityConfig.register(app);
        // Lærer Javalin at omdanne tekst fra fx en URL til UUID med UUID.fromString.
        // Bruges af pathParamAsClass("id", UUID.class); ugyldig tekst giver en valideringsfejl.
        app.unsafeConfig().validation.register(java.util.UUID.class, java.util.UUID::fromString);
        
        AuthorController authorController = new AuthorController();
        CategoryController categoryController = new CategoryController();
        BookController bookController = new BookController();

        // Hent flere med valgfri search, page og size.
        app.get(BASE_PATH + "/authors", authorController::findAll);
        app.get(BASE_PATH + "/categories", categoryController::findAll);
        app.get(BASE_PATH + "/books", bookController::findAll);

        // Hent en enkelt ressource via ID.
        app.get(BASE_PATH + "/authors/{id}", authorController::findById);
        app.get(BASE_PATH + "/categories/{id}", categoryController::findById);
        app.get(BASE_PATH + "/books/{id}", bookController::findById);

        app.post(BASE_PATH + "/etl/runs", new etl.EtlRunController()::run);

        // Opret, opdater og slet en bog.
        app.post(BASE_PATH + "/books", bookController::create);
        app.put(BASE_PATH + "/books/{id}", bookController::update);
        app.delete(BASE_PATH + "/books/{id}", bookController::delete);
    }
}
