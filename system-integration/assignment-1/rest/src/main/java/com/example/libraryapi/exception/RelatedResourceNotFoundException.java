package com.example.libraryapi.exception;

public class RelatedResourceNotFoundException extends RuntimeException {

    public RelatedResourceNotFoundException(String message) {
        super(message);
    }
}
