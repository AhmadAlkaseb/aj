package controller;

import routes.Routes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

// Samler kun opbygningen af links. Controllerne bestemmer, hvilke links der bruges.
public class Hateoas {
    public static Map<String, String> link(String href) {
        return Map.of("href", href);
    }

    public static Map<String, Object> pageLinks(
            String resource, String search, int page, int size, boolean hasNext) {
        Map<String, Object> links = new LinkedHashMap<>();
        links.put("self", link(pageUrl(resource, search, page, size)));
        links.put("first", link(pageUrl(resource, search, 0, size)));
        if (page > 0) {
            links.put("prev", link(pageUrl(resource, search, page - 1, size)));
        }
        if (hasNext) {
            links.put("next", link(pageUrl(resource, search, page + 1, size)));
        }
        return links;
    }

    private static String pageUrl(String resource, String search, int page, int size) {
        String url = Routes.BASE_PATH + "/" + resource + "?page=" + page + "&size=" + size;
        if (search != null) {
            url += "&search=" + URLEncoder.encode(search, StandardCharsets.UTF_8);
        }
        return url;
    }
}
