package com.example.libraryapi.service;

import com.example.libraryapi.exception.ResourceNotFoundException;
import com.example.libraryapi.model.Publisher;
import com.example.libraryapi.repository.PublisherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.libraryapi.repository.BookRepository;
import com.example.libraryapi.exception.ConflictException;

import java.util.List;

@Service
public class PublisherService {

    private final PublisherRepository publisherRepository;

    private final BookRepository bookRepository;

    public PublisherService(PublisherRepository publisherRepository, BookRepository bookRepository) {
        this.bookRepository = bookRepository;
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
        InputValidation.requireText(name, "Name");
        Publisher publisher = new Publisher(name);
        return publisherRepository.save(publisher);
    }

    public Publisher updatePublisher(Integer id, String name) {
        InputValidation.requireText(name, "Name");
        Publisher publisher = getPublisherById(id);
        publisher.setName(name);
        return publisherRepository.save(publisher);
    }

    @Transactional
    public boolean deletePublisher(Integer id) {
        Publisher publisher = getPublisherById(id);
        if (bookRepository.existsByPublisherId(id)) {
            throw new ConflictException("Cannot delete publisher: it is referenced by a book");
        }
        publisherRepository.delete(publisher);
        return true;
    }
}
