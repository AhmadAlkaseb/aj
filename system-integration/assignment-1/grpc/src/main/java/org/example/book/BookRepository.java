package org.example.book;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// Startdata læses fra SQLite; nye bøger gemmes kun i hukommelsen.
final class BookRepository {
    private final Map<Long, Book> books = new TreeMap<>();
    private long nextId = 1;

    // Indlæser bøgerne fra den medfølgende database.
    BookRepository() throws IOException, SQLException {
        // JDBC kræver en filsti, så ressourcen kopieres til en midlertidig fil.
        Path database = Files.createTempFile("book-seed-", ".db");
        try {
            try (InputStream input = BookRepository.class.getResourceAsStream("/library.db")) {
                if (input == null) {
                    throw new IOException("Startdatabasen library.db mangler.");
                }
                Files.copy(input, database, StandardCopyOption.REPLACE_EXISTING);
            }
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                 var statement = connection.createStatement();
                 var rows = statement.executeQuery(
                         "SELECT nBookID, cTitle, nAuthorID, nPublishingCompanyID, nPublishingYear FROM tbook")) {
                while (rows.next()) {
                    Book book = Book.newBuilder()
                            .setId(rows.getLong("nBookID"))
                            .setName(rows.getString("cTitle"))
                            .setAuthorId(rows.getLong("nAuthorID"))
                            .setPublisherId(rows.getLong("nPublishingCompanyID"))
                            .setPublicationYear(rows.getInt("nPublishingYear"))
                            .build();
                    books.put(book.getId(), book);
                    nextId = Math.max(nextId, book.getId() + 1);
                }
            }
        } finally {
            Files.deleteIfExists(database);
        }
    }

    // Finder en bog via ID.
    synchronized Book findById(long id) {
        return books.get(id);
    }

    // Bruges til at tælle bøger ved serverstart.
    synchronized List<Book> findAll() {
        return List.copyOf(books.values());
    }

    // Tildeler et nyt ID og gemmer bogen i hukommelsen.
    synchronized Book create(CreateBookRequest request) {
        Book book = Book.newBuilder()
                .setId(nextId++)
                .setName(request.getName().trim())
                .setAuthorId(request.getAuthorId())
                .setPublisherId(request.getPublisherId())
                .setPublicationYear(request.getPublicationYear())
                .build();
        books.put(book.getId(), book);
        return book;
    }
}
