package com.example.libraryapi.service;

import com.example.libraryapi.exception.ResourceNotFoundException;
import com.example.libraryapi.model.Publisher;
import com.example.libraryapi.repository.PublisherRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PublisherService {

    private final PublisherRepository publisherRepository;

    public PublisherService(PublisherRepository publisherRepository) {
        this.publisherRepository = publisherRepository;
    }

    public List<Publisher> getAllPublishers() {
        return publisherRepository.findAll();
    }

    public Publisher getPublisherById(Integer id) {
        return publisherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Publisher not found with id: " + id));
    }

    public Publisher createPublisher(String name) {
        Publisher publisher = new Publisher(name);
        return publisherRepository.save(publisher);
    }

    public Publisher updatePublisher(Integer id, String name) {
        Publisher publisher = getPublisherById(id);
        publisher.setName(name);
        return publisherRepository.save(publisher);
    }

    public boolean deletePublisher(Integer id) {
        Publisher publisher = getPublisherById(id);
        publisherRepository.delete(publisher);
        return true;
    }
}
