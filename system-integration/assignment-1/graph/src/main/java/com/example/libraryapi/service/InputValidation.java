package com.example.libraryapi.service;

import com.example.libraryapi.exception.ValidationException;

final class InputValidation {
    private InputValidation() {
    }

    static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field + " must not be blank");
        }
    }
}
