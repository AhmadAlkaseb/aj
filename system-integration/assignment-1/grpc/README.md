# gRPC Book Service

Et Java-projekt med Apache Dubbo Triple, protobuf og en bogservice.

## Proto-fil

Proto-kontrakten ligger her:

```text
src/main/proto/book_service.proto
```

Filen indeholder:

- `BookService`
- `getBookById`: henter en bog via ID
- `createBook`: opretter en bog
- `watchBooks`: streamer nye bøger til klienter
- `Book` og request/response-messages

Bogdata gemmes i øjeblikket kun i hukommelsen. Data forsvinder, når serveren lukkes.

## Generer stub-filer

Stub-filerne genereres automatisk fra proto-filen af `dubbo-maven-plugin`.

Kør fra projektets rodmappe:

```powershell
mvn clean compile
```

De genererede filer findes herefter under:

```text
target/generated-sources/protobuf/java/org/example/book/
```

## Start serveren

Start serveren i terminal 1:

```powershell
mvn -q exec:java "-Dexec.mainClass=org.example.book.BookServer"
```

Serveren bruger Dubbo Triple og lytter på port `50052`.

## Start klienten

Start klienten i terminal 2, mens serveren kører:

```powershell
mvn -q exec:java "-Dexec.mainClass=org.example.book.BookClient"
```

Klienten viser denne menu:

```text
1. Opret en bog
2. Find en bog
3. Stream bøger
4. Luk
```

Ved valg `3` modtager klienten nye bøger. Der oprettes automatisk en tilfældig bog hvert 20. sekund, så streamen kan ses i brug.

Hvis serveren ikke kører, viser klienten en enkel fejlbesked.

## Byg projektet

```powershell
mvn clean compile
```
