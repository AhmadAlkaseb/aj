# Library REST API

Spring Boot REST API for the library assignment, backed by SQLite.

## Run

```bash
mvn spring-boot:run
```

The application starts on `http://localhost:8081` by default.

## Standardized Startup

Use environment variables for startup configuration:

```bash
SPRING_APPLICATION_NAME=library-rest-api \
SERVER_PORT=8081 \
SPRING_DATASOURCE_URL=jdbc:sqlite:library-rest.db \
SPRING_DATASOURCE_DRIVER=org.sqlite.JDBC \
mvn spring-boot:run
```

## Endpoints

- `POST /authors`, `GET /authors`, `GET /authors/{id}`, `PUT /authors/{id}`, `DELETE /authors/{id}`
- `POST /publishers`, `GET /publishers`, `GET /publishers/{id}`, `PUT /publishers/{id}`, `DELETE /publishers/{id}`
- `POST /books`, `GET /books?limit=&offset=`, `GET /books/{id}`, `PUT /books/{id}`, `DELETE /books/{id}`

Open `library-api.http` in VS Code to send requests.
