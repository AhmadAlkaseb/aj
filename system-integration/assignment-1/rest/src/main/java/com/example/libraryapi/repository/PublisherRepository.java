package com.example.libraryapi.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.example.libraryapi.dto.PublisherRequest;
import com.example.libraryapi.exception.ResourceNotFoundException;
import com.example.libraryapi.model.Publisher;

@Repository
public class PublisherRepository {

    private static final String SELECT_ALL = "SELECT nPublishingCompanyID, cName FROM tpublishingcompany ORDER BY nPublishingCompanyID";
    private static final String SELECT_BY_ID = "SELECT nPublishingCompanyID, cName FROM tpublishingcompany WHERE nPublishingCompanyID = ?";
    private static final String INSERT = "INSERT INTO tpublishingcompany (cName) VALUES (?)";
    private static final String UPDATE = "UPDATE tpublishingcompany SET cName = ? WHERE nPublishingCompanyID = ?";
    private static final String DELETE = "DELETE FROM tpublishingcompany WHERE nPublishingCompanyID = ?";

    private final JdbcTemplate jdbcTemplate;

    public PublisherRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Publisher> findAll() {
        return jdbcTemplate.query(SELECT_ALL, (resultSet, rowNum) -> new Publisher(
                resultSet.getLong("nPublishingCompanyID"),
                resultSet.getString("cName")));
    }

    public Optional<Publisher> findById(long id) {
        List<Publisher> publishers = jdbcTemplate.query(SELECT_BY_ID, (resultSet, rowNum) -> new Publisher(
                resultSet.getLong("nPublishingCompanyID"),
                resultSet.getString("cName")),
                id);
        return publishers.stream().findFirst();
    }

    public Publisher create(PublisherRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, request.name().trim());
            return statement;
        }, keyHolder);

        Long id = keyHolder.getKey().longValue();
        return new Publisher(id, request.name().trim());
    }

    public Publisher update(long id, PublisherRequest request) {
        int updated = jdbcTemplate.update(UPDATE, request.name().trim(), id);
        if (updated == 0) {
            throw new ResourceNotFoundException("Publisher not found: " + id);
        }
        return findById(id).orElseThrow(() -> new ResourceNotFoundException("Publisher not found: " + id));
    }

    public void delete(long id) {
        int updated = jdbcTemplate.update(DELETE, id);
        if (updated == 0) {
            throw new ResourceNotFoundException("Publisher not found: " + id);
        }
    }

    public boolean existsById(long id) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM tpublishingcompany WHERE nPublishingCompanyID = ?", Integer.class, id);
        return count != null && count > 0;
    }
}
