package com.example.libraryapi.model;

public record Book(Long id, String title, Long authorId, Integer publishingYear, Long publishingCompanyId) {
}
