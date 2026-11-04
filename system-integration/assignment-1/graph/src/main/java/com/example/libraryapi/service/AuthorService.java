package com.example.libraryapi.service;

import com.example.libraryapi.exception.ResourceNotFoundException;
import com.example.libraryapi.model.Author;
import com.example.libraryapi.repository.AuthorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.libraryapi.repository.BookRepository;
import com.example.libraryapi.exception.ConflictException;

import java.util.List;

@Service
public class AuthorService {

    private final AuthorRepository authorRepository;

    private final BookRepository bookRepository;

    public AuthorService(AuthorRepository authorRepository, BookRepository bookRepository) {
        this.bookRepository = bookRepository;
        this.authorRepository = authorRepository;
    }

    public List<Author> getAllAuthors() {
        return authorRepository.findAll();
    }

    public Author getAuthorById(Integer id) {
        return authorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Author not found with id: " + id));
    }

    public Author createAuthor(String name, String surname) {
        InputValidation.requireText(name, "Name");
        InputValidation.requireText(surname, "Surname");
        Author author = new Author(name, surname);
        return authorRepository.save(author);
    }

    public Author updateAuthor(Integer id, String name, String surname) {
        InputValidation.requireText(name, "Name");
        InputValidation.requireText(surname, "Surname");
        Author author = getAuthorById(id);
        author.setName(name);
        author.setSurname(surname);
        return authorRepository.save(author);
    }

    @Transactional
    public boolean deleteAuthor(Integer id) {
        Author author = getAuthorById(id);
        if (bookRepository.existsByAuthorId(id)) {
            throw new ConflictException("Cannot delete author: it is referenced by a book");
        }
        authorRepository.delete(author);
        return true;
    }
}
