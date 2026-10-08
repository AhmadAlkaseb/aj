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
- `watchBooks`: streamer først alle eksisterende bøger og derefter nye bøger løbende
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

Serveren bruger Dubbo Triple og lytter på port `50053`.

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

Ved valg `3` modtager klienten alle eksisterende bøger og derefter nye bøger,
så snart de oprettes. Nye bøger oprettes manuelt via valg `1`.

Hvis serveren ikke kører, viser klienten en enkel fejlbesked.

## Test via HTTP

Åbn [book.http](book.http) i IntelliJ IDEA eller VS Code med en HTTP-client
og start serveren først. Filen indeholder requests til at:

- oprette en bog med `createBook`
- hente en bog via ID med `getBookById`
- starte en server-stream med `watchBooks`

Kør `Stream nye bøger`. Requesten returnerer først alle allerede registrerede
bøger og holder derefter forbindelsen åben, så nye bøger vises direkte, når
`Opret en bog` køres fra HTTP-filen eller en anden klient.

`gRPC Tester`-extensionen viser ikke server-streams korrekt, fordi den
behandler kaldet som et unary-kald og venter på, at streamen afsluttes.
Brug Java-klienten til at teste `watchBooks`:

```powershell
mvn -q exec:java "-Dexec.mainClass=org.example.book.BookClient"
```

## Byg projektet

```powershell
mvn clean compile
```
