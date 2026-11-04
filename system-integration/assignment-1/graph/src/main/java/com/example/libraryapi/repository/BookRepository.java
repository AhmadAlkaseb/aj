package com.example.libraryapi.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.example.libraryapi.model.Book;

@Repository
public class BookRepository {

    private static final RowMapper<Book> ROW_MAPPER = (rs, rowNum) -> {
        Book entity = new Book(rs.getString("cTitle"), rs.getInt("nAuthorID"), rs.getInt("nPublishingYear"), rs.getInt("nPublishingCompanyID"));
        entity.setId(rs.getInt("nBookID"));
        return entity;
    };

    private final JdbcTemplate jdbcTemplate;

    public BookRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /*public List<Book> findAll() {
        return jdbcTemplate.query("SELECT nBookID, cTitle, nAuthorID, nPublishingYear, nPublishingCompanyID FROM tbook", ROW_MAPPER);
    }
    */
    public Optional<Book> findById(Integer id) {
        return jdbcTemplate.query("SELECT nBookID, cTitle, nAuthorID, nPublishingYear, nPublishingCompanyID FROM tbook WHERE nBookID = ?", ROW_MAPPER, id)
                .stream().findFirst();
    }

    public boolean existsByAuthorId(Integer id) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM tbook WHERE nAuthorID = ?)", Boolean.class, id));
    }

    public boolean existsByPublisherId(Integer id) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM tbook WHERE nPublishingCompanyID = ?)", Boolean.class, id));
    }

    public Book save(Book entity) {
        if (entity.getId() == null) {
            GeneratedKeyHolder keys = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO tbook (cTitle, nAuthorID, nPublishingYear, nPublishingCompanyID) VALUES (?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setObject(1, entity.getTitle());
                statement.setObject(2, entity.getAuthorId());
                statement.setObject(3, entity.getPublishingYear());
                statement.setObject(4, entity.getPublishingCompanyId());
                return statement;
            }, keys);
            entity.setId(Objects.requireNonNull(keys.getKey(), "Insert did not return an ID").intValue());
        } else {
            jdbcTemplate.update("UPDATE tbook SET cTitle = ?, nAuthorID = ?, nPublishingYear = ?, nPublishingCompanyID = ? WHERE nBookID = ?",
                    entity.getTitle(), entity.getAuthorId(), entity.getPublishingYear(), entity.getPublishingCompanyId(), entity.getId());
        }
        return entity;
    }

    public void delete(Book entity) {
        jdbcTemplate.update("DELETE FROM tbook WHERE nBookID = ?", entity.getId());
    }
}
