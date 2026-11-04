package com.example.libraryapi.model;

public class Book {

    private Integer id;

    private String title;

    private Integer authorId;

    private Integer publishingYear;

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
