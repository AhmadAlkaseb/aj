package persistence;

import etl.ShardBootstrapper;
import etl.DatabaseResourceManager;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.List;
import java.util.UUID;

public final class DatabaseSeeder {
    private DatabaseSeeder() { }

    private record Example(String author, String title, String category) { }

    private static final List<Example> EXAMPLES = List.of(
            new Example("Jane Austen", "Pride and Prejudice", "Romance"),
            new Example("George Orwell", "Nineteen Eighty-Four", "Dystopia"),
            new Example("J. R. R. Tolkien", "The Hobbit", "Fantasy"),
            new Example("Mary Shelley", "Frankenstein", "Gothic fiction"),
            new Example("Agatha Christie", "Murder on the Orient Express", "Crime"),
            new Example("Arthur Conan Doyle", "The Adventures of Sherlock Holmes", "Detective fiction"),
            new Example("H. G. Wells", "The Time Machine", "Science fiction"),
            new Example("Bram Stoker", "Dracula", "Horror"),
            new Example("Mark Twain", "The Adventures of Tom Sawyer", "Adventure"),
            new Example("Oscar Wilde", "The Picture of Dorian Gray", "Philosophical fiction"),
            new Example("Charles Dickens", "Oliver Twist", "Social novel"),
            new Example("Charlotte Brontë", "Jane Eyre", "Bildungsroman"),
            new Example("Emily Brontë", "Wuthering Heights", "Tragic romance"),
            new Example("Herman Melville", "Moby-Dick", "Nautical fiction"),
            new Example("Nathaniel Hawthorne", "The Scarlet Letter", "Historical fiction"),
            new Example("Louisa May Alcott", "Little Women", "Coming-of-age fiction"),
            new Example("Lewis Carroll", "Alice's Adventures in Wonderland", "Literary nonsense"),
            new Example("Frances Hodgson Burnett", "The Secret Garden", "Children's fiction"),
            new Example("Kenneth Grahame", "The Wind in the Willows", "Animal fiction"),
            new Example("Beatrix Potter", "The Tale of Peter Rabbit", "Picture book"),
            new Example("A. A. Milne", "Winnie-the-Pooh", "Children's fantasy"),
            new Example("Lucy Maud Montgomery", "Anne of Green Gables", "Domestic fiction"),
            new Example("J. M. Barrie", "Peter and Wendy", "Fairy tale"),
            new Example("Carlo Collodi", "The Adventures of Pinocchio", "Didactic fiction"),
            new Example("Robert Louis Stevenson", "Treasure Island", "Pirate fiction"),
            new Example("Jules Verne", "Twenty Thousand Leagues Under the Seas", "Scientific romance"),
            new Example("Edgar Rice Burroughs", "A Princess of Mars", "Planetary romance"),
            new Example("Jack London", "The Call of the Wild", "Wilderness fiction"),
            new Example("Rudyard Kipling", "The Jungle Book", "Animal stories"),
            new Example("Daniel Defoe", "Robinson Crusoe", "Robinsonade"),
            new Example("Jonathan Swift", "Gulliver's Travels", "Satire"),
            new Example("Miguel de Cervantes", "Don Quixote", "Picaresque fiction"),
            new Example("Voltaire", "Candide", "Philosophical satire"),
            new Example("Victor Hugo", "Les Misérables", "Epic novel"),
            new Example("Alexandre Dumas", "The Count of Monte Cristo", "Revenge fiction"),
            new Example("Leo Tolstoy", "War and Peace", "War novel"),
            new Example("Fyodor Dostoevsky", "Crime and Punishment", "Psychological fiction"),
            new Example("Franz Kafka", "The Metamorphosis", "Absurdist fiction"),
            new Example("Robert W. Chambers", "The King in Yellow", "Weird fiction"),
            new Example("H. P. Lovecraft", "The Call of Cthulhu", "Cosmic horror"),
            new Example("M. R. James", "Ghost Stories of an Antiquary", "Ghost stories"),
            new Example("Wilkie Collins", "The Woman in White", "Sensation fiction"),
            new Example("Gaston Leroux", "The Phantom of the Opera", "Gothic romance"),
            new Example("Joseph Conrad", "Heart of Darkness", "Colonial literature"),
            new Example("George Eliot", "Middlemarch", "Realist fiction"),
            new Example("Thomas Hardy", "Tess of the d'Urbervilles", "Tragedy"),
            new Example("Virginia Woolf", "Mrs Dalloway", "Modernist fiction"),
            new Example("James Joyce", "Dubliners", "Short stories"),
            new Example("Walt Whitman", "Leaves of Grass", "Poetry"),
            new Example("Henry David Thoreau", "Walden", "Nature writing"));

    private static UUID id(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    // Bevidst nulstilling af den almindelige undervisningsdatabase ved hver Main-opstart.
    public static void seed() throws SQLException {
        try (Connection connection = DatabaseResourceManager.connect(DatabaseResourceManager.normalUrl())) {
            connection.setAutoCommit(false);
            try (Statement sql = connection.createStatement()) {
                // Kun bogkatalogets tre tabeller. Keycloak og Redis berøres ikke.
                sql.execute("DROP TABLE IF EXISTS books");
                sql.execute("DROP TABLE IF EXISTS authors");
                sql.execute("DROP TABLE IF EXISTS categories");
                sql.execute("CREATE TABLE authors(id UUID PRIMARY KEY, name VARCHAR(255) NOT NULL)");
                sql.execute("CREATE TABLE categories(id UUID PRIMARY KEY, name VARCHAR(255) NOT NULL)");
                sql.execute("""
                        CREATE TABLE books(
                            id VARCHAR(255) PRIMARY KEY, title VARCHAR(255) NOT NULL,
                            author_id UUID NOT NULL REFERENCES authors(id),
                            category_id UUID NOT NULL REFERENCES categories(id))
                        """);
                try (PreparedStatement author = connection.prepareStatement("INSERT INTO authors VALUES (?, ?)");
                     PreparedStatement category = connection.prepareStatement("INSERT INTO categories VALUES (?, ?)");
                     PreparedStatement book = connection.prepareStatement("INSERT INTO books VALUES (?, ?, ?, ?)")) {
                    for (Example example : EXAMPLES) {
                        UUID authorId = id("author:" + example.author());
                        UUID categoryId = id(example.category());
                        author.setObject(1, authorId);
                        author.setString(2, example.author());
                        author.executeUpdate();
                        category.setObject(1, categoryId);
                        category.setString(2, example.category());
                        category.executeUpdate();
                        book.setString(1, authorId + "_" + id("book:" + example.title()));
                        book.setString(2, example.title());
                        book.setObject(3, authorId);
                        book.setObject(4, categoryId);
                        book.executeUpdate();
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
        // Importér den nye opstartsportion til shards ved næste ETL-kørsel.
        ShardBootstrapper.schedule();
    }
}
