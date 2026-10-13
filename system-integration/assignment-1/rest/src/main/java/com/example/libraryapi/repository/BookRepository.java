package com.example.libraryapi.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.example.libraryapi.dto.BookRequest;
import com.example.libraryapi.exception.ResourceNotFoundException;
import com.example.libraryapi.model.Book;

@Repository
public class BookRepository {

    private static final String SELECT_ALL = """
            SELECT nBookID, cTitle, nAuthorID, nPublishingYear, nPublishingCompanyID
            FROM tbook
            ORDER BY nBookID
            LIMIT ? OFFSET ?
            """;
    private static final String SELECT_BY_ID = """
            SELECT nBookID, cTitle, nAuthorID, nPublishingYear, nPublishingCompanyID
            FROM tbook
            WHERE nBookID = ?
            """;
    private static final String INSERT = """
            INSERT INTO tbook (cTitle, nAuthorID, nPublishingYear, nPublishingCompanyID)
            VALUES (?, ?, ?, ?)
            """;
    private static final String UPDATE = """
            UPDATE tbook
            SET cTitle = ?, nAuthorID = ?, nPublishingYear = ?, nPublishingCompanyID = ?
            WHERE nBookID = ?
            """;
    private static final String DELETE = "DELETE FROM tbook WHERE nBookID = ?";

    private final JdbcTemplate jdbcTemplate;

    public BookRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Book> findAll(int limit, int offset) {
        return jdbcTemplate.query(SELECT_ALL, (resultSet, rowNum) -> new Book(
                resultSet.getLong("nBookID"),
                resultSet.getString("cTitle"),
                resultSet.getLong("nAuthorID"),
                resultSet.getInt("nPublishingYear"),
                resultSet.getLong("nPublishingCompanyID")),
                limit, offset);
    }

    public Optional<Book> findById(long id) {
        List<Book> books = jdbcTemplate.query(SELECT_BY_ID, (resultSet, rowNum) -> new Book(
                resultSet.getLong("nBookID"),
                resultSet.getString("cTitle"),
                resultSet.getLong("nAuthorID"),
                resultSet.getInt("nPublishingYear"),
                resultSet.getLong("nPublishingCompanyID")),
                id);
        return books.stream().findFirst();
    }

    public Book create(BookRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, request.title().trim());
            statement.setLong(2, request.authorId());
            statement.setInt(3, request.publishingYear());
            statement.setLong(4, request.publishingCompanyId());
            return statement;
        }, keyHolder);

        Long id = keyHolder.getKey().longValue();
        return new Book(id, request.title().trim(), request.authorId(), request.publishingYear(), request.publishingCompanyId());
    }

    public Book update(long id, BookRequest request) {
        int updated = jdbcTemplate.update(UPDATE,
                request.title().trim(),
                request.authorId(),
                request.publishingYear(),
                request.publishingCompanyId(),
                id);
        if (updated == 0) {
            throw new ResourceNotFoundException("Book not found: " + id);
        }
        return findById(id).orElseThrow(() -> new ResourceNotFoundException("Book not found: " + id));
    }

    public void delete(long id) {
        int updated = jdbcTemplate.update(DELETE, id);
        if (updated == 0) {
            throw new ResourceNotFoundException("Book not found: " + id);
        }
    }
}
