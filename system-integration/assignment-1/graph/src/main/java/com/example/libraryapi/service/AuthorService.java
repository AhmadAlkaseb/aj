package com.example.libraryapi.service;

import com.example.libraryapi.exception.ResourceNotFoundException;
import com.example.libraryapi.model.Author;
import com.example.libraryapi.repository.AuthorRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthorService {

    private final AuthorRepository authorRepository;

    public AuthorService(AuthorRepository authorRepository) {
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
        Author author = new Author(name, surname);
        return authorRepository.save(author);
    }

    public Author updateAuthor(Integer id, String name, String surname) {
        Author author = getAuthorById(id);
        author.setName(name);
        author.setSurname(surname);
        return authorRepository.save(author);
    }

    public boolean deleteAuthor(Integer id) {
        Author author = getAuthorById(id);
        authorRepository.delete(author);
        return true;
    }
}
