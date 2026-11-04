package com.example.libraryapi.service;

import com.example.libraryapi.exception.ResourceNotFoundException;
import com.example.libraryapi.exception.ValidationException;
import org.springframework.transaction.annotation.Transactional;
import com.example.libraryapi.model.Book;
import com.example.libraryapi.repository.AuthorRepository;
import com.example.libraryapi.repository.BookRepository;
import com.example.libraryapi.repository.PublisherRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BookService {

    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final PublisherRepository publisherRepository;

    public BookService(BookRepository bookRepository,
                      AuthorRepository authorRepository,
                      PublisherRepository publisherRepository) {
        this.bookRepository = bookRepository;
        this.authorRepository = authorRepository;
        this.publisherRepository = publisherRepository;
    }

    /*public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }
    */
    public Book getBookById(Integer id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with id: " + id));
    }

    @Transactional
    public Book createBook(String title, Integer authorId, Integer publishingYear, Integer publishingCompanyId) {
        InputValidation.requireText(title, "Title");
        validateReferences(authorId, publishingCompanyId);
        Book book = new Book(title, authorId, publishingYear, publishingCompanyId);
        return bookRepository.save(book);
    }

    @Transactional
    public Book updateBook(Integer id, String title, Integer authorId, Integer publishingYear, Integer publishingCompanyId) {
        InputValidation.requireText(title, "Title");
        Book existingBook = getBookById(id);
        validateReferences(authorId, publishingCompanyId);

        existingBook.setTitle(title);
        existingBook.setAuthorId(authorId);
        existingBook.setPublishingYear(publishingYear);
        existingBook.setPublishingCompanyId(publishingCompanyId);

        return bookRepository.save(existingBook);
    }

    public boolean deleteBook(Integer id) {
        Book book = getBookById(id);
        bookRepository.delete(book);
        return true;
    }

    private void validateReferences(Integer authorId, Integer publishingCompanyId) {
        authorRepository.findById(authorId)
                .orElseThrow(() -> new ValidationException("Author not found with id: " + authorId));

        publisherRepository.findById(publishingCompanyId)
                .orElseThrow(() -> new ValidationException("Publisher not found with id: " + publishingCompanyId));
    }
}
