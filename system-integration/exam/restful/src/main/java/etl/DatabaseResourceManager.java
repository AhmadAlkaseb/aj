package etl;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.Configuration;
import persistence.model.*;
import java.sql.*;

public final class DatabaseResourceManager {
    // Én genbrugt Hibernate-factory pr. shard. Hvert DAO-kald opretter sin egen EntityManager.
    private static final EntityManagerFactory[] FACTORIES = new EntityManagerFactory[3];
    private DatabaseResourceManager() { }

    public static String sourceUrl(int shard) {
        // Afviser shardnumre uden for de tre databaser, inden arrayet bruges.
        if (shard < 0 || shard > 2) throw new IllegalArgumentException("Shard skal være 0, 1 eller 2");
        // Miljøvariablen kan overskrive standarden. Lokalt bruges port 5433, 5434 eller 5435.
        return System.getenv().getOrDefault("SHARD_" + shard + "_URL",
                "jdbc:postgresql://localhost:" + (5433 + shard) + "/bookcatalog");
    }

    public static String normalUrl() {
        // Den almindelige database indeholder startsættet med 50 rækker pr. tabel.
        return System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/bookcatalog");
    }

    public static String warehouseUrl() {
        // Warehouse er en separat database til de konsoliderede ETL-data.
        return System.getenv().getOrDefault("WAREHOUSE_URL", "jdbc:postgresql://localhost:5436/warehouse");
    }

    public static Connection connect(String url) throws SQLException {
        // Åbner en JDBC-forbindelse. Den kaldende metode lukker den med try-with-resources.
        return DriverManager.getConnection(url,
                System.getenv().getOrDefault("DB_USER", "bookuser"),
                System.getenv().getOrDefault("DB_PASSWORD", "bookpass"));
    }

    public static synchronized EntityManagerFactory factory(int shard) {
        // synchronized forhindrer to tråde i at oprette samme factory samtidig.
        String url = sourceUrl(shard);
        // Opretter først forbindelsesopsætningen, når den bruges, eller hvis den gamle er lukket.
        if (FACTORIES[shard] == null || !FACTORIES[shard].isOpen()) {
            FACTORIES[shard] = new Configuration()
                    // Samme entities giver samme tabelstruktur på alle tre shards.
                    .addAnnotatedClass(Author.class).addAnnotatedClass(Book.class).addAnnotatedClass(Category.class)
                    .setProperty("hibernate.connection.driver_class", "org.postgresql.Driver")
                    .setProperty("hibernate.connection.url", url)
                    .setProperty("hibernate.connection.username", System.getenv().getOrDefault("DB_USER", "bookuser"))
                    .setProperty("hibernate.connection.password", System.getenv().getOrDefault("DB_PASSWORD", "bookpass"))
                    // Hibernate opretter manglende tabeller og tilpasser skemaet uden at nulstille det.
                    .setProperty("hibernate.hbm2ddl.auto", "update").buildSessionFactory();
        }
        return FACTORIES[shard];
    }

    public static void initialize() {
        // Sørger for, at alle tre shards har en factory og deres tabeller.
        for (int shard = 0; shard < 3; shard++) factory(shard);
    }

    public static synchronized void close() {
        // Lukker kun factories, som faktisk er oprettet og stadig er åbne.
        for (EntityManagerFactory factory : FACTORIES) {
            if (factory != null && factory.isOpen()) factory.close();
        }
    }
}
