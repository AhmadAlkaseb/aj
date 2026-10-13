package com.example.libraryapi.controller;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.libraryapi.dto.AuthorRequest;
import com.example.libraryapi.model.Author;
import com.example.libraryapi.service.AuthorService;

@RestController
@RequestMapping("/authors")
@Validated
public class AuthorController {

    private final AuthorService authorService;

    public AuthorController(AuthorService authorService) {
        this.authorService = authorService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Author create(@Valid @RequestBody AuthorRequest request) {
        return authorService.create(request);
    }

    @GetMapping("/{id}")
    public Author findById(@PathVariable long id) {
        return authorService.getById(id);
    }

    @GetMapping
    public List<Author> findAll() {
        return authorService.findAll();
    }

    @PutMapping("/{id}")
    public Author update(@PathVariable long id, @Valid @RequestBody AuthorRequest request) {
        return authorService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        authorService.delete(id);
    }
}
