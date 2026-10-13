package com.example.libraryapi.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.libraryapi.dto.AuthorRequest;
import com.example.libraryapi.model.Author;
import com.example.libraryapi.repository.AuthorRepository;

@Service
public class AuthorService {

    private final AuthorRepository authorRepository;

    public AuthorService(AuthorRepository authorRepository) {
        this.authorRepository = authorRepository;
    }

    public Author create(AuthorRequest request) {
        return authorRepository.create(request);
    }

    public Author getById(long id) {
        return authorRepository.findById(id).orElseThrow(() -> new com.example.libraryapi.exception.ResourceNotFoundException("Author not found: " + id));
    }

    public List<Author> findAll() {
        return authorRepository.findAll();
    }

    public Author update(long id, AuthorRequest request) {
        return authorRepository.update(id, request);
    }

    public void delete(long id) {
        authorRepository.delete(id);
    }
}
