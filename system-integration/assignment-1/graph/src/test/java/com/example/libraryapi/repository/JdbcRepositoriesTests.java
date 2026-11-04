package com.example.libraryapi.repository;

import com.example.libraryapi.model.Author;
import com.example.libraryapi.model.Book;
import com.example.libraryapi.model.Publisher;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.junit.jupiter.api.Assertions.*;

class JdbcRepositoriesTests {

    @Test
    void persistsReadsUpdatesAndDeletesAllModelsWithSQLite() {
        try (SingleConnectionDataSource source = new SingleConnectionDataSource("jdbc:sqlite::memory:", true)) {
            new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(source);
            JdbcTemplate jdbc = new JdbcTemplate(source);
            AuthorRepository authors = new AuthorRepository(jdbc);
            PublisherRepository publishers = new PublisherRepository(jdbc);
            BookRepository books = new BookRepository(jdbc);

            Author author = authors.save(new Author("Jane", "Austen"));
            Publisher publisher = publishers.save(new Publisher("Acme"));
            Book book = books.save(new Book("Pride and Prejudice", author.getId(), 1813, publisher.getId()));

            assertNotNull(author.getId());
            assertNotNull(publisher.getId());
            assertNotNull(book.getId());
            assertEquals("Jane", authors.findById(author.getId()).orElseThrow().getName());
            assertEquals("Acme", publishers.findById(publisher.getId()).orElseThrow().getName());
            Book stored = books.findById(book.getId()).orElseThrow();
            assertEquals("Pride and Prejudice", stored.getTitle());
            assertEquals(author.getId(), stored.getAuthorId());
            assertEquals(publisher.getId(), stored.getPublishingCompanyId());
            assertEquals(1813, stored.getPublishingYear());
            assertEquals(1, authors.findAll().size());
            assertEquals(1, publishers.findAll().size());
            //assertEquals(1, books.findAll().size());

            Author otherAuthor = authors.save(new Author("Other", "Author"));
            Publisher otherPublisher = publishers.save(new Publisher("Other Press"));
            author.setName("Updated");
            author.setSurname("Surname");
            authors.save(author);
            publisher.setName("Updated Press");
            publishers.save(publisher);
            book.setTitle("Updated Title");
            book.setPublishingYear(2000);
            book.setAuthorId(otherAuthor.getId());
            book.setPublishingCompanyId(otherPublisher.getId());
            books.save(book);

            assertEquals("Updated", authors.findById(author.getId()).orElseThrow().getName());
            assertEquals("Surname", authors.findById(author.getId()).orElseThrow().getSurname());
            assertEquals("Updated Press", publishers.findById(publisher.getId()).orElseThrow().getName());
            stored = books.findById(book.getId()).orElseThrow();
            assertEquals("Updated Title", stored.getTitle());
            assertEquals(2000, stored.getPublishingYear());
            assertEquals(otherAuthor.getId(), stored.getAuthorId());
            assertEquals(otherPublisher.getId(), stored.getPublishingCompanyId());

            books.delete(book);
            authors.delete(author);
            publishers.delete(publisher);
            assertTrue(books.findById(book.getId()).isEmpty());
            assertTrue(authors.findById(author.getId()).isEmpty());
            assertTrue(publishers.findById(publisher.getId()).isEmpty());
        }
    }
}
