# Library REST API

Spring Boot REST API for the library assignment, backed by SQLite.

## Run

```bash
mvn spring-boot:run
```

The application starts on `http://localhost:8080`.

## Endpoints

- `POST /authors`, `GET /authors`, `GET /authors/{id}`, `PUT /authors/{id}`, `DELETE /authors/{id}`
- `POST /publishers`, `GET /publishers`, `GET /publishers/{id}`, `PUT /publishers/{id}`, `DELETE /publishers/{id}`
- `POST /books`, `GET /books?limit=&offset=`, `GET /books/{id}`, `PUT /books/{id}`, `DELETE /books/{id}`

Open `library-api.http` in VS Code to send requests.
