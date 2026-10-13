# Library API GraphQL Service

This project exposes a GraphQL API on the `/graphql` endpoint with PostgreSQL persistence.

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker + Docker Compose

## Run locally with Postgres

1. Start PostgreSQL:
   ```bash
   docker compose up -d postgres
   ```
2. Run the app:
   ```bash
   ./mvnw spring-boot:run
   ```
   If you do not have the wrapper, use:
   ```bash
   mvn spring-boot:run
   ```
3. Open GraphQL playground at:
   ```text
   http://localhost:8080/graphiql
   ```

## Run everything with Docker Compose

```bash
docker compose up --build
```

## Example GraphQL requests

### Query a book by ID
```graphql
query {
  bookById(id: 1) {
    id
    title
    authorId
    publishingYear
    publishingCompanyId
  }
}
```

### List all authors
```graphql
query {
  authors {
    id
    name
    surname
  }
}
```

### List all publishers
```graphql
query {
  publishers {
    id
    name
  }
}
```

### Create a book
```graphql
mutation {
  createBook(title: "The Hobbit", authorId: 1, publishingYear: 1937, publishingCompanyId: 1) {
    id
    title
  }
}
```
