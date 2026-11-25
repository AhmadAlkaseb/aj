package etl;

import persistence.model.*;
import java.sql.*;
import java.util.*;

// Et forberedende trin: normal database -> hash-fordelte shards.
// Den egentlige ETL læser derefter alle tre shards -> warehouse.
public final class ShardBootstrapper {
    // Husker i denne Java-proces, om et nyt startsæt afventer fordeling.
    private static boolean pending;
    private ShardBootstrapper() { }

    // Kaldes af DatabaseSeeder efter nulstilling af den almindelige database.
    public static synchronized void schedule() { pending = true; }

    public static synchronized void distributeIfPending() throws SQLException {
        // Uden et nyt startsæt springes fordelingen over. synchronized forhindrer samtidige fordelinger.
        if (!pending) return;
        List<Author> authors = new ArrayList<>();
        List<Category> categories = new ArrayList<>();
        List<Book> books = new ArrayList<>();
        // Opslagstabeller bruges til at forbinde bøger med de allerede læste entities.
        Map<UUID, Author> authorsById = new HashMap<>();
        Map<UUID, Category> categoriesById = new HashMap<>();

        try (Connection source = DatabaseResourceManager.connect(DatabaseResourceManager.normalUrl())) {
            // Alle SELECTs her ser samme snapshot af den almindelige database.
            // Auto-commit slås fra, så læsningen holdes i én read-only transaktion.
            source.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            source.setAutoCommit(false);
            source.setReadOnly(true);
            try (Statement sql = source.createStatement()) {
                try (ResultSet rows = sql.executeQuery("SELECT id, name FROM authors")) {
                    while (rows.next()) {
                        Author author = new Author(rows.getObject("id", UUID.class), rows.getString("name"));
                        authors.add(author);
                        authorsById.put(author.getId(), author);
                    }
                }
                try (ResultSet rows = sql.executeQuery("SELECT id, name FROM categories")) {
                    while (rows.next()) {
                        Category category = new Category(rows.getObject("id", UUID.class), rows.getString("name"));
                        categories.add(category);
                        categoriesById.put(category.getId(), category);
                    }
                }
                try (ResultSet rows = sql.executeQuery("SELECT id, title, author_id, category_id FROM books")) {
                    while (rows.next()) {
                        // Genskaber relationerne ud fra foreign key-værdierne i kilden.
                        books.add(new Book(rows.getString("id"), rows.getString("title"),
                                authorsById.get(rows.getObject("author_id", UUID.class)),
                                categoriesById.get(rows.getObject("category_id", UUID.class))));
                    }
                }
                source.commit();
            } catch (SQLException | RuntimeException exception) {
                source.rollback();
                throw exception;
            }
        }

        // Hver shard har sin egen transaktion. Dette er ikke én samlet transaktion over tre databaser.
        for (int shard = 0; shard < 3; shard++) {
            try (var em = DatabaseResourceManager.factory(shard).createEntityManager()) {
                var tx = em.getTransaction();
                try {
                    tx.begin();
                    // Erstatter den valgte shards tidligere startsæt. Bøger slettes før deres relationer.
                    em.createQuery("delete from Book").executeUpdate();
                    em.createQuery("delete from Author").executeUpdate();
                    em.createQuery("delete from Category").executeUpdate();
                    // Referencedata kopieres til alle shards, så lokale foreign keys virker.
                    for (Category category : categories) {
                        em.persist(new Category(category.getId(), category.getName()));
                    }
                    for (Author author : authors) {
                        // Kun forfattere, hvis hash peger på denne shard, indsættes her.
                        if (ShardRouting.forAuthor(author.getId()) == shard) {
                            em.persist(new Author(author.getId(), author.getName()));
                        }
                    }
                    // Sender forfattere og kategorier til databasen, før bøgernes foreign keys oprettes.
                    em.flush();
                    for (Book book : books) {
                        if (ShardRouting.forBook(book.getId()) == shard) {
                            // em.find henter entities fra denne EntityManager, så relationerne er lokale.
                            em.persist(new Book(book.getId(), book.getTitle(),
                                    em.find(Author.class, book.getAuthor().getId()),
                                    em.find(Category.class, book.getCategory().getId())));
                        }
                    }
                    // Gemmer hele denne shards startsæt samlet.
                    tx.commit();
                } catch (RuntimeException exception) {
                    // Ruller denne shard tilbage. Tidligere committed shards kan genindlæses ved et nyt forsøg.
                    if (tx.isActive()) tx.rollback();
                    throw exception;
                }
            }
        }
        // Gentagen ETL i samme opstart bevarer API-ændringer i shards.
        // Ved en fejl bliver pending stående, så hele importen kan gentages.
        pending = false;
    }
}
