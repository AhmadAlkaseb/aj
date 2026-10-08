package org.example.book;

import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

// Holder bøgerne i hukommelsen, så de kan oprettes og slås op.
final class BookRepository {
    // Tildeler hvert nyt bogobjekt et stigende ID.
    private final AtomicLong nextId = new AtomicLong(1);
    private final Map<Long, Book> books = new ConcurrentHashMap<>();

    // Finder en bog ud fra dens ID.
    Book findById(long id) {
        return books.get(id);
    }

    List<Book> findAll() {
        return books.values().stream()
                .sorted((first, second) -> Long.compare(first.getId(), second.getId()))
                .toList();
    }

    // Opretter og gemmer en bog ud fra klientens oplysninger.
    Book create(CreateBookRequest request) {
        long id = nextId.getAndIncrement();
        Book book = Book.newBuilder()
                .setId(id)
                .setName(request.getName())
                .setAuthorId(request.getAuthorId())
                .setPublisherId(request.getPublisherId())
                .setPublicationYear(request.getPublicationYear())
                .build();
        books.put(id, book);
        return book;
    }
}
