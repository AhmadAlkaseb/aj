package com.example.libraryapi.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_id")
    private Integer id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "author_id", nullable = false)
    private Integer authorId;

    @Column(name = "publishing_year", nullable = false)
    private Integer publishingYear;

    @Column(name = "publishing_company_id", nullable = false)
    private Integer publishingCompanyId;

    public Book() {
    }

    public Book(String title, Integer authorId, Integer publishingYear, Integer publishingCompanyId) {
        this.title = title;
        this.authorId = authorId;
        this.publishingYear = publishingYear;
        this.publishingCompanyId = publishingCompanyId;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Integer authorId) {
        this.authorId = authorId;
    }

    public Integer getPublishingYear() {
        return publishingYear;
    }

    public void setPublishingYear(Integer publishingYear) {
        this.publishingYear = publishingYear;
    }

    public Integer getPublishingCompanyId() {
        return publishingCompanyId;
    }

    public void setPublishingCompanyId(Integer publishingCompanyId) {
        this.publishingCompanyId = publishingCompanyId;
    }
}
