package com.example.libraryapi;

import com.example.libraryapi.controller.AuthorController;
import com.example.libraryapi.controller.PublisherController;
import com.example.libraryapi.exception.RestExceptionHandler;
import com.example.libraryapi.repository.AuthorRepository;
import com.example.libraryapi.repository.PublisherRepository;
import com.example.libraryapi.service.AuthorService;
import com.example.libraryapi.service.PublisherService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DeleteConflictTests {
    @Test
    void referencedParentsReturnConflictAndUnreferencedParentsCanBeDeleted() throws Exception {
        try (SingleConnectionDataSource source = new SingleConnectionDataSource("jdbc:sqlite::memory:", true)) {
            new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(source);
            JdbcTemplate jdbc = new JdbcTemplate(source);
            MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new AuthorController(new AuthorService(new AuthorRepository(jdbc))),
                    new PublisherController(new PublisherService(new PublisherRepository(jdbc))))
                    .setControllerAdvice(new RestExceptionHandler()).build();
            jdbc.update("INSERT INTO tauthor (nAuthorID, cName, cSurname) VALUES (1, 'Jane', 'Doe')");
            jdbc.update("INSERT INTO tpublishingcompany (nPublishingCompanyID, cName) VALUES (1, 'Press')");
            jdbc.update("INSERT INTO tbook (cTitle, nAuthorID, nPublishingYear, nPublishingCompanyID) VALUES ('Title', 1, 2000, 1)");
            mvc.perform(delete("/authors/1")).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Cannot delete an author or publisher referenced by a book"));
            mvc.perform(delete("/publishers/1")).andExpect(status().isConflict());
            mvc.perform(delete("/authors/999")).andExpect(status().isNotFound());
            jdbc.update("DELETE FROM tbook");
            mvc.perform(delete("/authors/1")).andExpect(status().isNoContent());
            mvc.perform(delete("/publishers/1")).andExpect(status().isNoContent());
        }
    }
}
