package com.example.libraryapi.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.example.libraryapi.dto.AuthorRequest;
import com.example.libraryapi.exception.ResourceNotFoundException;
import com.example.libraryapi.model.Author;

@Repository
public class AuthorRepository {

    private static final String SELECT_ALL = "SELECT nAuthorID, cName, cSurname FROM tauthor ORDER BY nAuthorID";
    private static final String SELECT_BY_ID = "SELECT nAuthorID, cName, cSurname FROM tauthor WHERE nAuthorID = ?";
    private static final String INSERT = "INSERT INTO tauthor (cName, cSurname) VALUES (?, ?)";
    private static final String UPDATE = "UPDATE tauthor SET cName = ?, cSurname = ? WHERE nAuthorID = ?";
    private static final String DELETE = "DELETE FROM tauthor WHERE nAuthorID = ?";

    private final JdbcTemplate jdbcTemplate;

    public AuthorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Author> findAll() {
        return jdbcTemplate.query(SELECT_ALL, (resultSet, rowNum) -> new Author(
                resultSet.getLong("nAuthorID"),
                resultSet.getString("cName"),
                resultSet.getString("cSurname")));
    }

    public Optional<Author> findById(long id) {
        List<Author> authors = jdbcTemplate.query(SELECT_BY_ID, (resultSet, rowNum) -> new Author(
                resultSet.getLong("nAuthorID"),
                resultSet.getString("cName"),
                resultSet.getString("cSurname")),
                id);
        return authors.stream().findFirst();
    }

    public Author create(AuthorRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, request.name().trim());
            statement.setString(2, request.surname().trim());
            return statement;
        }, keyHolder);

        Long id = keyHolder.getKey().longValue();
        return new Author(id, request.name().trim(), request.surname().trim());
    }

    public Author update(long id, AuthorRequest request) {
        int updated = jdbcTemplate.update(UPDATE, request.name().trim(), request.surname().trim(), id);
        if (updated == 0) {
            throw new ResourceNotFoundException("Author not found: " + id);
        }
        return findById(id).orElseThrow(() -> new ResourceNotFoundException("Author not found: " + id));
    }

    public void delete(long id) {
        int updated = jdbcTemplate.update(DELETE, id);
        if (updated == 0) {
            throw new ResourceNotFoundException("Author not found: " + id);
        }
    }

    public boolean existsById(long id) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM tauthor WHERE nAuthorID = ?", Integer.class, id);
        return count != null && count > 0;
    }
}
