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

import com.example.libraryapi.model.Author;

@Repository
public class AuthorRepository {

    private static final RowMapper<Author> ROW_MAPPER = (rs, rowNum) -> {
        Author entity = new Author(rs.getString("cName"), rs.getString("cSurname"));
        entity.setId(rs.getInt("nAuthorID"));
        return entity;
    };

    private final JdbcTemplate jdbcTemplate;

    public AuthorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Author> findAll() {
        return jdbcTemplate.query("SELECT nAuthorID, cName, cSurname FROM tauthor", ROW_MAPPER);
    }

    public Optional<Author> findById(Integer id) {
        return jdbcTemplate.query("SELECT nAuthorID, cName, cSurname FROM tauthor WHERE nAuthorID = ?", ROW_MAPPER, id)
                .stream().findFirst();
    }

    public Author save(Author entity) {
        if (entity.getId() == null) {
            GeneratedKeyHolder keys = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO tauthor (cName, cSurname) VALUES (?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setObject(1, entity.getName());
                statement.setObject(2, entity.getSurname());
                return statement;
            }, keys);
            entity.setId(Objects.requireNonNull(keys.getKey(), "Insert did not return an ID").intValue());
        } else {
            jdbcTemplate.update("UPDATE tauthor SET cName = ?, cSurname = ? WHERE nAuthorID = ?",
                    entity.getName(), entity.getSurname(), entity.getId());
        }
        return entity;
    }

    public void delete(Author entity) {
        jdbcTemplate.update("DELETE FROM tauthor WHERE nAuthorID = ?", entity.getId());
    }
}
