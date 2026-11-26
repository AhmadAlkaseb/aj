package app.p3repository;

import app.p1model.Product;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRepository {
    // Håndterer SQL-kald og databaseforbindelser.
    private final JdbcTemplate jdbc;
    
    // Omdanner en række fra databasen til et Product-objekt
    private final RowMapper<Product> mapper = (rs, row) ->
            new Product(rs.getInt("id"), rs.getString("name"), rs.getBigDecimal("price"));

    public ProductRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // Finder et produkt. Optional er tom, hvis id ikke findes.
    public Optional<Product> find(int id) {
        // ? binder input som data og beskytter mod SQL-injection.
        return jdbc.query("SELECT id, name, price FROM products WHERE id = ?", mapper, id)
                .stream().findFirst();
    }

    // Henter alle produkter sorteret efter id.
    public List<Product> findAll() {
        return jdbc.query("SELECT id, name, price FROM products ORDER BY id", mapper);
    }

    // Opretter et produkt. RETURNING id giver os databasens genererede id.
    public Product create(String name, BigDecimal price) {
        int id = jdbc.queryForObject("INSERT INTO products (name, price) VALUES (?, ?) RETURNING id",
                Integer.class, name, price);
        return new Product(id, name, price);
    }

    // Returnerer true, hvis præcis en række blev opdateret.
    public boolean update(int id, String name, BigDecimal price) {
        return jdbc.update("UPDATE products SET name = ?, price = ? WHERE id = ?", name, price, id) == 1;
    }
}
