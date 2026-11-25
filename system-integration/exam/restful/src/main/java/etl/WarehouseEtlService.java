package etl;

import java.sql.*;
import java.util.*;

// Extract læser alle kilder først. Load er én transaktion i samledatabasen.
public class WarehouseEtlService {
    // Små dataobjekter til de udtrukne rækker. shard gemmer, hvilken kilde rækken kommer fra.
    public record NamedRow(int shard, String id, String name) { }
    public record BookRow(int shard, String id, String title, String authorId, String categoryId) { }
    // Samler de læste rækker fra alle tre shards. Result indeholder statistik til klienten.
    public record Snapshot(List<NamedRow> authors, List<NamedRow> categories, List<BookRow> books) { }
    public record Result(UUID runId, int shards, int authors, int categories, int books) { }

    public Snapshot extract() throws SQLException {
        // EXTRACT: læs alle kilder færdigt, før data indlæses i warehouse.
        List<NamedRow> authors = new ArrayList<>();
        List<NamedRow> categories = new ArrayList<>();
        List<BookRow> books = new ArrayList<>();
        for (int shard = 0; shard < 3; shard++) {
            try (Connection source = DatabaseResourceManager.connect(DatabaseResourceManager.sourceUrl(shard))) {
                // Et stabilt snapshot pr. shard, ikke ét fælles snapshot over alle tre databaser.
                source.setReadOnly(true);
                source.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                source.setAutoCommit(false);
                try (Statement sql = source.createStatement()) {
                    // SQL-teksterne er faste og indeholder ikke sammenkædet brugerinput.
                    try (ResultSet rows = sql.executeQuery("SELECT id, name FROM authors")) {
                        while (rows.next()) {
                            String id = rows.getString("id");
                            // Kontrollerer, at den fysiske placering svarer til hash-funktionen.
                            if (ShardRouting.forAuthor(UUID.fromString(id)) != shard) {
                                throw new SQLException("Forfatter findes på forkert shard");
                            }
                            authors.add(new NamedRow(shard, id, rows.getString("name")));
                        }
                    }
                    try (ResultSet rows = sql.executeQuery("SELECT id, name FROM categories")) {
                        // Kategorikopier fra forskellige shards bevares med deres kilde-shard.
                        while (rows.next()) categories.add(new NamedRow(shard, rows.getString("id"), rows.getString("name")));
                    }
                    try (ResultSet rows = sql.executeQuery("SELECT id, title, author_id, category_id FROM books")) {
                        while (rows.next()) {
                            String id = rows.getString("id");
                            // Både shardnummeret og forfatteren skal passe til bogens sammensatte ID.
                            if (ShardRouting.forBook(id) != shard
                                    || !ShardRouting.authorFromBook(id).toString().equals(rows.getString("author_id"))) {
                                throw new SQLException("Bog findes på forkert shard eller har forkert forfatter");
                            }
                            books.add(new BookRow(shard, id, rows.getString("title"),
                                    rows.getString("author_id"), rows.getString("category_id")));
                        }
                    }
                    source.commit();
                } catch (SQLException | RuntimeException exception) {
                    // Fejlen sendes videre, så vi aldrig fortsætter med et ufuldstændigt udtræk.
                    source.rollback();
                    throw exception;
                }
            }
        }
        return new Snapshot(authors, categories, books);
    }

    public Result run() throws SQLException {
        // Hvis en kilde fejler, starter load slet ikke.
        DatabaseResourceManager.initialize();
        try (Connection destination = DatabaseResourceManager.connect(DatabaseResourceManager.warehouseUrl())) {
            destination.setAutoCommit(false);
            // Warehouse ændres kun, hvis hele transaktionen kan gennemføres.
            try (Statement sql = destination.createStatement()) {
                // Samtidige ETL-kørsler mod denne warehouse-database venter på samme lås.
                // Låsen frigives automatisk ved commit eller rollback.
                sql.execute("SELECT pg_advisory_xact_lock(727271)");
                // Forberedelse: fordel et eventuelt nyt startsæt. Senere kørsler springer dette over.
                ShardBootstrapper.distributeIfPending();
                Snapshot data = extract();
                // Et nyt kørsels-ID bruges til at markere alle rækker, vi ser i denne kørsel.
                UUID runId = UUID.randomUUID();
                createSchema(sql);
                // LOAD: indlæs forfattere og kategorier før bøger, så relationerne findes.
                loadNames(destination, "authors", data.authors(), runId);
                loadNames(destination, "categories", data.categories(), runId);
                // UPSERT: samme (source_shard, source_id) opdateres frem for at blive duplikeret.
                // Spørgsmålstegn bindes som værdier og beskytter mod SQL-injection.
                try (PreparedStatement insert = destination.prepareStatement("""
                        INSERT INTO books(source_shard, source_id, title, title_normalized, author_id, category_id, seen_run)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (source_shard, source_id) DO UPDATE SET
                        title=EXCLUDED.title, title_normalized=EXCLUDED.title_normalized,
                        author_id=EXCLUDED.author_id, category_id=EXCLUDED.category_id, seen_run=EXCLUDED.seen_run
                        """)) {
                    for (BookRow book : data.books()) {
                        insert.setInt(1, book.shard());
                        // TRANSFORM: ID'et får et kilde-shard som del af destinationens nøgle.
                        insert.setString(2, book.id());
                        insert.setString(3, book.title());
                        // Originalteksten bevares; normaliseret tekst kan bruges til rapportering.
                        insert.setString(4, book.title().strip().toLowerCase(Locale.ROOT));
                        insert.setString(5, book.authorId());
                        insert.setString(6, book.categoryId());
                        insert.setObject(7, runId);
                        // Samler indsættelserne, så de kan sendes som en batch.
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                // seen_run opdateres for hver indlæst række. Et ældre kørsels-ID betyder,
                // at rækken er forsvundet fra kilderne og skal fjernes. Bøger slettes først pga. foreign keys.
                for (String table : List.of("books", "authors", "categories")) {
                    try (PreparedStatement delete = destination.prepareStatement(
                            "DELETE FROM " + table + " WHERE seen_run <> ?")) {
                        delete.setObject(1, runId);
                        delete.executeUpdate();
                    }
                }
                // Først nu bliver hele det nye resultat synligt for andre forbindelser.
                destination.commit();
                return new Result(runId, 3, data.authors().size(), data.categories().size(), data.books().size());
            } catch (SQLException | RuntimeException exception) {
                // Gendanner warehouse til tilstanden før kørslen, hvis et trin fejler.
                destination.rollback();
                throw exception;
            }
        }
    }

    private void createSchema(Statement sql) throws SQLException {
        // Opretter kun tabeller, hvis de mangler. Eksisterende data nulstilles ikke her.
        // Den sammensatte primærnøgle adskiller identiske kilde-ID'er fra forskellige shards.
        for (String table : List.of("authors", "categories")) {
            sql.execute("CREATE TABLE IF NOT EXISTS " + table
                    + "(source_shard INT NOT NULL, source_id TEXT NOT NULL, name TEXT NOT NULL,"
                    + " seen_run UUID NOT NULL, PRIMARY KEY(source_shard, source_id))");
        }
        // Foreign keys bruger også kilde-shard, så en bog peger på relationerne fra den rigtige kilde.
        sql.execute("""
                CREATE TABLE IF NOT EXISTS books (
                    source_shard INT NOT NULL,
                    source_id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    title_normalized TEXT NOT NULL,
                    author_id TEXT NOT NULL,
                    category_id TEXT NOT NULL,
                    seen_run UUID NOT NULL,
                    PRIMARY KEY(source_shard, source_id),
                    FOREIGN KEY(source_shard, author_id) REFERENCES authors(source_shard, source_id),
                    FOREIGN KEY(source_shard, category_id) REFERENCES categories(source_shard, source_id)
                )
                """);
    }

    private void loadNames(Connection destination, String table, List<NamedRow> rows, UUID runId) throws SQLException {
        // Samme indlæsningsmetode bruges til de to tabeller, som begge har id og name.
        // table vælges kun internt mellem authors og categories, aldrig fra brugerinput.
        try (PreparedStatement insert = destination.prepareStatement(
                "INSERT INTO " + table + "(source_shard, source_id, name, seen_run) VALUES (?, ?, ?, ?)"
                        + " ON CONFLICT (source_shard, source_id) DO UPDATE SET"
                        + " name=EXCLUDED.name, seen_run=EXCLUDED.seen_run")) {
            for (NamedRow row : rows) {
                // Bevarer det oprindelige navn og markerer rækken som set i denne kørsel.
                insert.setInt(1, row.shard());
                insert.setString(2, row.id());
                insert.setString(3, row.name());
                insert.setObject(4, runId);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }
}
