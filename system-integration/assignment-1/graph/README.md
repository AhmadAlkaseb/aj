# Library API GraphQL Service

This project exposes a GraphQL API on the `/graphql` endpoint with SQLite persistence through Spring JDBC `JdbcTemplate`.

Tables are initialized from `src/main/resources/schema.sql`. The API reads the supplied `tauthor`,
`tpublishingcompany`, and `tbook` tables. Existing data is retained.

## Prerequisites

- Java 21+
- Maven 3.9+

## Standardized Startup

Use environment variables for startup configuration:

```bash
SPRING_APPLICATION_NAME=library-graph-api \
SERVER_PORT=8080 \
SPRING_DATASOURCE_URL=jdbc:sqlite:library.db \
SPRING_DATASOURCE_DRIVER=org.sqlite.JDBC \
mvn spring-boot:run
```

Open GraphQL playground at:

```text
http://localhost:8080/graphiql
```

## Example GraphQL requests

### Query a book by ID
```graphql
query {
  bookById(id: 1000) {
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

Start from the `graph` directory so the default database path resolves to
`graph/library.db`, or set `SPRING_DATASOURCE_URL` to an absolute SQLite path.
