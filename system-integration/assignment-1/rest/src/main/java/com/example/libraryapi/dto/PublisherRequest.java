package com.example.libraryapi.dto;

import jakarta.validation.constraints.NotBlank;

public record PublisherRequest(
        @NotBlank(message = "Publisher name is required") String name) {
}
