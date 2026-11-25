package controller;

import dao.BookDAO;
import routes.Routes;
import dao.AuthorDAO;
import dao.CategoryDAO;
import persistence.model.Author;
import persistence.model.Category;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import persistence.model.Book;

import java.util.Map;
import java.util.UUID;
import etl.ShardRouting;

public class BookController {
    private final BookDAO dao = BookDAO.getInstance();

    public void findAll(Context ctx) {
        String search = ctx.queryParam("search");
        int page = ctx.queryParamAsClass("page", Integer.class)
                .check(value -> value >= 0, "page skal være mindst 0")
                .getOrDefault(0);
        int size = ctx.queryParamAsClass("size", Integer.class)
                .check(value -> value >= 1 && value <= 100, "size skal være mellem 1 og 100")
                .getOrDefault(10);
        if ((long) page * size > Integer.MAX_VALUE) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("message", "Sidetallet er for stort"));
            return;
        }
        var items = dao.findAll(search, page, size);
        // Vis kun next, hvis der faktisk findes resultater på den næste side.
        boolean hasNext = ((long) page + 1) * size <= Integer.MAX_VALUE
                && !dao.findAll(search, page + 1, size).isEmpty();
        var response = Map.of(
                "items", items.stream().map(this::toResponse).toList(),
                "page", page,
                "size", size,
                "_links", Hateoas.pageLinks("books", search, page, size, hasNext));
        ctx.status(HttpStatus.OK).json(response);
    }

    public void findById(Context ctx) {
        String id = ctx.pathParamAsClass("id", String.class)
                .check(value -> {
                    try { ShardRouting.forBook(value); return true; }
                    catch (IllegalArgumentException exception) { return false; }
                }, "Ugyldigt bog-ID").get();
        Book book = dao.findById(id);
        if (book == null) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("message", "Bogen findes ikke"));
            return;
        }
        ctx.status(HttpStatus.OK).json(toResponse(book));
    }

    // Request-body indeholder kun de felter, klienten må bestemme.
    public record BookRequest(String title, UUID authorId, UUID categoryId) {
    }

    public void create(Context ctx) {
        Book book = readBook(ctx);
        if (book == null) {
            return;
        }
        Book created = dao.create(book);
        ctx.header("Location", Routes.BASE_PATH + "/books/" + created.getId());
        ctx.status(HttpStatus.CREATED).json(toResponse(created));
    }

    public void update(Context ctx) {
        String id = ctx.pathParamAsClass("id", String.class)
                .check(value -> {
                    try { ShardRouting.forBook(value); return true; }
                    catch (IllegalArgumentException exception) { return false; }
                }, "Ugyldigt bog-ID").get();
        Book existing = dao.findById(id);
        if (existing == null) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("message", "Bogen findes ikke"));
            return;
        }
        Book book = readBook(ctx);
        if (book == null) {
            return;
        }
        if (!existing.getAuthor().getId().equals(book.getAuthor().getId())) {
            ctx.status(HttpStatus.CONFLICT).json(Map.of("message", "Forfatteren kan ikke ændres på en eksisterende bog"));
            return;
        }
        Book updated = dao.update(id, book);
        if (updated == null) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("message", "Bogen findes ikke"));
            return;
        }
        // Henter også forfatter og kategori, før EntityManager lukkes.
        ctx.status(HttpStatus.OK).json(toResponse(dao.findById(updated.getId())));
    }

    public void delete(Context ctx) {
        String id = ctx.pathParamAsClass("id", String.class)
                .check(value -> {
                    try { ShardRouting.forBook(value); return true; }
                    catch (IllegalArgumentException exception) { return false; }
                }, "Ugyldigt bog-ID").get();
        if (!dao.delete(id)) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("message", "Bogen findes ikke"));
            return;
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private Book readBook(Context ctx) {
        BookRequest request = ctx.bodyValidator(BookRequest.class)
                .check(body -> body != null && body.title() != null && !body.title().isBlank(),
                        "Titel skal udfyldes")
                .check(body -> body != null && (body.title() == null || body.title().length() <= 255), "Titel må højst være 255 tegn")
                .check(body -> body != null && body.authorId() != null,
                        "authorId skal være et UUID")
                .check(body -> body != null && body.categoryId() != null,
                        "categoryId skal være et UUID")
                .get();

        Author author = AuthorDAO.getInstance().findById(request.authorId());
        Category category = CategoryDAO.getInstance().findById(request.categoryId());
        if (author == null || category == null) {
            ctx.status(HttpStatus.BAD_REQUEST)
                    .json(Map.of("message", "Forfatter eller kategori findes ikke"));
            return null;
        }
        return new Book(request.title(), author, category);
    }

    private Map<String, Object> toResponse(Book book) {
        return Map.of(
                "id", book.getId(),
                "title", book.getTitle(),
                "author", book.getAuthor(),
                "category", book.getCategory(),
                "_links", Map.of(
                        "self", Hateoas.link(Routes.BASE_PATH + "/books/" + book.getId()),
                        "collection", Hateoas.link(Routes.BASE_PATH + "/books"),
                        "author", Hateoas.link(Routes.BASE_PATH + "/authors/" + book.getAuthor().getId()),
                        "category", Hateoas.link(Routes.BASE_PATH + "/categories/" + book.getCategory().getId())));
    }
}
