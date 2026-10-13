package com.example.libraryapi.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BookRequest(
        @NotBlank(message = "Book title is required") String title,
        @NotNull(message = "Author id is required") Long authorId,
        @NotNull(message = "Publishing year is required") @Min(value = 0, message = "Publishing year must be non-negative") @Max(value = 9999, message = "Publishing year must have at most four digits") Integer publishingYear,
        @NotNull(message = "Publishing company id is required") Long publishingCompanyId) {
}
