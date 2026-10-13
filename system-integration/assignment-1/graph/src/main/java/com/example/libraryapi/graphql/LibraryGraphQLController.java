package com.example.libraryapi.graphql;

import com.example.libraryapi.model.Author;
import com.example.libraryapi.model.Book;
import com.example.libraryapi.model.Publisher;
import com.example.libraryapi.service.AuthorService;
import com.example.libraryapi.service.BookService;
import com.example.libraryapi.service.PublisherService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class LibraryGraphQLController {

    private final BookService bookService;
    private final AuthorService authorService;
    private final PublisherService publisherService;

    public LibraryGraphQLController(BookService bookService,
                                   AuthorService authorService,
                                   PublisherService publisherService) {
        this.bookService = bookService;
        this.authorService = authorService;
        this.publisherService = publisherService;
    }

    @QueryMapping
    public Book bookById(@Argument Integer id) {
        return bookService.getBookById(id);
    }

    @QueryMapping
    public List<Book> books() {
        return bookService.getAllBooks();
    }

    @QueryMapping
    public List<Author> authors() {
        return authorService.getAllAuthors();
    }

    @QueryMapping
    public List<Publisher> publishers() {
        return publisherService.getAllPublishers();
    }

    @MutationMapping
    public Book createBook(@Argument String title,
                           @Argument Integer authorId,
                           @Argument Integer publishingYear,
                           @Argument Integer publishingCompanyId) {
        return bookService.createBook(title, authorId, publishingYear, publishingCompanyId);
    }

    @MutationMapping
    public Book updateBook(@Argument Integer id,
                           @Argument String title,
                           @Argument Integer authorId,
                           @Argument Integer publishingYear,
                           @Argument Integer publishingCompanyId) {
        return bookService.updateBook(id, title, authorId, publishingYear, publishingCompanyId);
    }

    @MutationMapping
    public Boolean deleteBook(@Argument Integer id) {
        return bookService.deleteBook(id);
    }

    @MutationMapping
    public Author createAuthor(@Argument String name, @Argument String surname) {
        return authorService.createAuthor(name, surname);
    }

    @MutationMapping
    public Author updateAuthor(@Argument Integer id, @Argument String name, @Argument String surname) {
        return authorService.updateAuthor(id, name, surname);
    }

    @MutationMapping
    public Boolean deleteAuthor(@Argument Integer id) {
        return authorService.deleteAuthor(id);
    }

    @MutationMapping
    public Publisher createPublisher(@Argument String name) {
        return publisherService.createPublisher(name);
    }

    @MutationMapping
    public Publisher updatePublisher(@Argument Integer id, @Argument String name) {
        return publisherService.updatePublisher(id, name);
    }

    @MutationMapping
    public Boolean deletePublisher(@Argument Integer id) {
        return publisherService.deletePublisher(id);
    }
}
