package com.example.libraryapi.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.libraryapi.dto.BookRequest;
import com.example.libraryapi.exception.RelatedResourceNotFoundException;
import com.example.libraryapi.model.Book;
import com.example.libraryapi.repository.BookRepository;
import com.example.libraryapi.repository.AuthorRepository;
import com.example.libraryapi.repository.PublisherRepository;

@Service
public class BookService {

    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final PublisherRepository publisherRepository;

    public BookService(BookRepository bookRepository, AuthorRepository authorRepository, PublisherRepository publisherRepository) {
        this.bookRepository = bookRepository;
        this.authorRepository = authorRepository;
        this.publisherRepository = publisherRepository;
    }

    public Book create(BookRequest request) {
        ensureRelatedResourcesExist(request.authorId(), request.publishingCompanyId());
        return bookRepository.create(request);
    }

    public Book getById(long id) {
        return bookRepository.findById(id).orElseThrow(() -> new com.example.libraryapi.exception.ResourceNotFoundException("Book not found: " + id));
    }

    public List<Book> findAll(int limit, int offset) {
        return bookRepository.findAll(limit, offset);
    }

    public Book update(long id, BookRequest request) {
        ensureRelatedResourcesExist(request.authorId(), request.publishingCompanyId());
        return bookRepository.update(id, request);
    }

    public void delete(long id) {
        bookRepository.delete(id);
    }

    private void ensureRelatedResourcesExist(long authorId, long publisherId) {
        if (!authorRepository.existsById(authorId)) {
            throw new RelatedResourceNotFoundException("Author not found: " + authorId);
        }
        if (!publisherRepository.existsById(publisherId)) {
            throw new RelatedResourceNotFoundException("Publisher not found: " + publisherId);
        }
    }
}
