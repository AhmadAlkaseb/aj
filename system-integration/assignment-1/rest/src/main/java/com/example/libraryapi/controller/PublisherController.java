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

import com.example.libraryapi.dto.PublisherRequest;
import com.example.libraryapi.model.Publisher;
import com.example.libraryapi.service.PublisherService;

@RestController
@RequestMapping("/publishers")
@Validated
public class PublisherController {

    private final PublisherService publisherService;

    public PublisherController(PublisherService publisherService) {
        this.publisherService = publisherService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Publisher create(@Valid @RequestBody PublisherRequest request) {
        return publisherService.create(request);
    }

    @GetMapping("/{id}")
    public Publisher findById(@PathVariable long id) {
        return publisherService.getById(id);
    }

    @GetMapping
    public List<Publisher> findAll() {
        return publisherService.findAll();
    }

    @PutMapping("/{id}")
    public Publisher update(@PathVariable long id, @Valid @RequestBody PublisherRequest request) {
        return publisherService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        publisherService.delete(id);
    }
}
