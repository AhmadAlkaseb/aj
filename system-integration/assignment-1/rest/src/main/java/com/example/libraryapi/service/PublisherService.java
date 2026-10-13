package com.example.libraryapi.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.libraryapi.dto.PublisherRequest;
import com.example.libraryapi.model.Publisher;
import com.example.libraryapi.repository.PublisherRepository;

@Service
public class PublisherService {

    private final PublisherRepository publisherRepository;

    public PublisherService(PublisherRepository publisherRepository) {
        this.publisherRepository = publisherRepository;
    }

    public Publisher create(PublisherRequest request) {
        return publisherRepository.create(request);
    }

    public Publisher getById(long id) {
        return publisherRepository.findById(id).orElseThrow(() -> new com.example.libraryapi.exception.ResourceNotFoundException("Publisher not found: " + id));
    }

    public List<Publisher> findAll() {
        return publisherRepository.findAll();
    }

    public Publisher update(long id, PublisherRequest request) {
        return publisherRepository.update(id, request);
    }

    public void delete(long id) {
        publisherRepository.delete(id);
    }
}
