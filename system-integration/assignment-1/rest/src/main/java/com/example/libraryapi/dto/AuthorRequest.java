package com.example.libraryapi.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthorRequest(
        @NotBlank(message = "Author name is required") String name,
        @NotBlank(message = "Author surname is required") String surname) {
}
