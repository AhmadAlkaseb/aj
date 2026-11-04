package com.example.libraryapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.test.context.TestExecutionListeners(
        listeners = org.springframework.test.context.support.DependencyInjectionTestExecutionListener.class,
        inheritListeners = false)
class LibraryApiApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @org.junit.jupiter.api.BeforeEach
    void clearData() {
        jdbc.update("DELETE FROM tbook");
        jdbc.update("DELETE FROM tauthor");
        jdbc.update("DELETE FROM tpublishingcompany");
        jdbc.update("ALTER TABLE tbook ALTER COLUMN nBookID RESTART WITH 1");
    }

    private org.springframework.test.web.servlet.ResultActions query(String query) throws Exception {
        return mockMvc.perform(post("/graphql").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("query", query))))
                .andExpect(status().isOk());
    }

    @Test
    void reportsMissingEntitiesInvalidInputAndConflictingDeletes() throws Exception {
        query("{ bookById(id: 999) { id } }")
                .andExpect(jsonPath("$.errors[0].extensions.code").value("NOT_FOUND"));
        for (String mutation : java.util.List.of(
                "createAuthor(name: \" \", surname: \"Doe\")",
                "createAuthor(name: \"Jane\", surname: \" \")",
                "createPublisher(name: \"\")",
                "createBook(title: \" \", authorId: 999, publishingCompanyId: 999, publishingYear: 2000)",
                "createBook(title: \"Title\", authorId: 999, publishingCompanyId: 999, publishingYear: 2000)")) {
            query("mutation { " + mutation + " { id } }")
                    .andExpect(jsonPath("$.errors[0].extensions.code").value("BAD_REQUEST"));
        }
        jdbc.update("INSERT INTO tauthor (nAuthorID, cName, cSurname) VALUES (100, 'Jane', 'Doe')");
        jdbc.update("INSERT INTO tpublishingcompany (nPublishingCompanyID, cName) VALUES (100, 'Press')");
        jdbc.update("INSERT INTO tbook (nBookID, cTitle, nAuthorID, nPublishingYear, nPublishingCompanyID) VALUES (100, 'Title', 100, 2000, 100)");
        for (String mutation : java.util.List.of("deleteAuthor(id: 100)", "deletePublisher(id: 100)")) {
            query("mutation { " + mutation + " }")
                    .andExpect(jsonPath("$.errors[0].extensions.code").value("CONFLICT"));
        }
        for (String mutation : java.util.List.of(
                "updateAuthor(id: 100, name: \" \", surname: \"Doe\")",
                "updatePublisher(id: 100, name: \" \")",
                "updateBook(id: 100, title: \" \", authorId: 100, publishingCompanyId: 100, publishingYear: 2000)")) {
            query("mutation { " + mutation + " { id } }")
                    .andExpect(jsonPath("$.errors[0].extensions.code").value("BAD_REQUEST"));
        }
        query("mutation { deleteBook(id: 100) }")
                .andExpect(jsonPath("$.data.deleteBook").value(true));
        query("mutation { deleteAuthor(id: 100) }")
                .andExpect(jsonPath("$.data.deleteAuthor").value(true));
        query("mutation { deletePublisher(id: 100) }")
                .andExpect(jsonPath("$.data.deletePublisher").value(true));
    }

    @Test
    void shouldCreateAuthorPublisherAndBookAndListThem() throws Exception {
        String publisherResponse = mockMvc.perform(post("/graphql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"mutation { createPublisher(name: \\\"Acme Press\\\") { id name } }\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createPublisher.name").value("Acme Press"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode publisherRoot = objectMapper.readTree(publisherResponse);
        Integer publisherId = publisherRoot.path("data").path("createPublisher").path("id").asInt();

        String authorResponse = mockMvc.perform(post("/graphql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"mutation { createAuthor(name: \\\"Jane\\\", surname: \\\"Austen\\\") { id name surname } }\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createAuthor.name").value("Jane"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode authorRoot = objectMapper.readTree(authorResponse);
        Integer authorId = authorRoot.path("data").path("createAuthor").path("id").asInt();

        mockMvc.perform(post("/graphql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"mutation { createBook(title: \\\"Pride and Prejudice\\\", authorId: " + authorId + ", publishingYear: 1813, publishingCompanyId: " + publisherId + ") { id title authorId publishingYear publishingCompanyId } }\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createBook.title").value("Pride and Prejudice"));

        mockMvc.perform(post("/graphql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"{ bookById(id: 1) { id title authorId publishingYear publishingCompanyId } authors { id name surname } publishers { id name } }\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookById.title").value("Pride and Prejudice"))
                .andExpect(jsonPath("$.data.authors[0].name").value("Jane"))
                .andExpect(jsonPath("$.data.publishers[0].name").value("Acme Press"));
    }
}
