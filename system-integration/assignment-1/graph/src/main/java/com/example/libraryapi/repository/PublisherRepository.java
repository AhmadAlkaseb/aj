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

import com.example.libraryapi.model.Publisher;

@Repository
public class PublisherRepository {

    private static final RowMapper<Publisher> ROW_MAPPER = (rs, rowNum) -> {
        Publisher entity = new Publisher(rs.getString("cName"));
        entity.setId(rs.getInt("nPublishingCompanyID"));
        return entity;
    };

    private final JdbcTemplate jdbcTemplate;

    public PublisherRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Publisher> findAll() {
        return jdbcTemplate.query("SELECT nPublishingCompanyID, cName FROM tpublishingcompany", ROW_MAPPER);
    }

    public Optional<Publisher> findById(Integer id) {
        return jdbcTemplate.query("SELECT nPublishingCompanyID, cName FROM tpublishingcompany WHERE nPublishingCompanyID = ?", ROW_MAPPER, id)
                .stream().findFirst();
    }

    public Publisher save(Publisher entity) {
        if (entity.getId() == null) {
            GeneratedKeyHolder keys = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO tpublishingcompany (cName) VALUES (?)",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setObject(1, entity.getName());
                return statement;
            }, keys);
            entity.setId(Objects.requireNonNull(keys.getKey(), "Insert did not return an ID").intValue());
        } else {
            jdbcTemplate.update("UPDATE tpublishingcompany SET cName = ? WHERE nPublishingCompanyID = ?",
                    entity.getName(), entity.getId());
        }
        return entity;
    }

    public void delete(Publisher entity) {
        jdbcTemplate.update("DELETE FROM tpublishingcompany WHERE nPublishingCompanyID = ?", entity.getId());
    }
}
