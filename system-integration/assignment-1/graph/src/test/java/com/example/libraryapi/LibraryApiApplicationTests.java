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
class LibraryApiApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
