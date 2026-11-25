package controller;

import dao.CategoryDAO;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import persistence.model.Category;

import routes.Routes;

import java.util.Map;
import java.util.UUID;

public class CategoryController {
    private final CategoryDAO dao = CategoryDAO.getInstance();

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
                "_links", Hateoas.pageLinks("categories", search, page, size, hasNext));
        ctx.status(HttpStatus.OK).json(response);
    }

    public void findById(Context ctx) {
        UUID id = ctx.pathParamAsClass("id", UUID.class).get();
        Category category = dao.findById(id);
        if (category == null) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("message", "Kategorien findes ikke"));
            return;
        }
        ctx.status(HttpStatus.OK).json(toResponse(category));
    }

    private Map<String, Object> toResponse(Category category) {
        return Map.of(
                "id", category.getId(),
                "name", category.getName(),
                "_links", Map.of(
                        "self", Hateoas.link(Routes.BASE_PATH + "/categories/" + category.getId()),
                        "collection", Hateoas.link(Routes.BASE_PATH + "/categories")));
    }
}
