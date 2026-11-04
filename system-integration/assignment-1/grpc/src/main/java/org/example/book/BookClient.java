package org.example.book;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public final class BookClient {

    // Start uden argumenter, eller brug Run i editoren:
    // mvn -q exec:java "-Dexec.mainClass=org.example.book.BookClient"
    // Skriv get, create eller watch. Klienten spørger selv efter oplysningerne.
    public static void main(String[] args) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 50052)
                .usePlaintext()
                .build();
        Runtime.getRuntime().addShutdownHook(new Thread(channel::shutdownNow));

        try (Scanner scanner = new Scanner(System.in)) {
            var service = BookServiceGrpc.newBlockingStub(channel);
            String command = readInput(scanner, "Skriv get, create eller watch:");

            switch (command) {
                case "get" -> getBook(scanner, service);
                case "create" -> createBook(scanner, service);
                case "watch" -> watchBooks(service);
                default -> System.out.println("Ukendt kommando.");
            }
        } catch (NumberFormatException exception) {
            System.out.println("ID og år skal være hele tal.");
        } catch (StatusRuntimeException exception) {
            if (exception.getStatus().getCode() == Status.Code.NOT_FOUND) {
                System.out.println("Ikke fundet");
            } else if (exception.getStatus().getCode() == Status.Code.UNAVAILABLE) {
                System.out.println("Serveren er ikke tilgængelig.");
            } else {
                System.out.println("Fejl: " + exception.getStatus().getDescription());
            }
        } finally {
            channel.shutdownNow();
        }
    }

    // Spørger efter ID, henter bogen og viser svaret.
    private static void getBook(Scanner scanner, BookServiceGrpc.BookServiceBlockingStub service) {
        long id = Long.parseLong(readInput(scanner, "Bog-ID:"));
        var request = GetBookByIdRequest.newBuilder().setBookId(id).build();
        Book book = service.withDeadlineAfter(5, TimeUnit.SECONDS).getBookById(request);
        printBook(book);
    }

    // Spørger efter bogens oplysninger og sender dem til serveren.
    private static void createBook(Scanner scanner, BookServiceGrpc.BookServiceBlockingStub service) {
        String title = readInput(scanner, "Bogtitel:");
        long authorId = Long.parseLong(readInput(scanner, "Forfatter-ID:"));
        long publisherId = Long.parseLong(readInput(scanner, "Forlag-ID:"));
        int year = Integer.parseInt(readInput(scanner, "Udgivelsesår:"));

        var request = CreateBookRequest.newBuilder()
                .setName(title)
                .setAuthorId(authorId)
                .setPublisherId(publisherId)
                .setPublicationYear(year)
                .build();
        var response = service.withDeadlineAfter(5, TimeUnit.SECONDS).createBook(request);
        System.out.println("Oprettet:");
        printBook(response.getBook());
    }

    // Holder abonnementet åbent og viser nye bøger, indtil klienten stoppes med Ctrl+C.
    private static void watchBooks(BookServiceGrpc.BookServiceBlockingStub service) {
        System.out.println("Abonnerer på nye bøger. Stop med Ctrl+C.");
        // Ingen tidsgrænse: vi skal kunne vente på fremtidige bøger.
        var books = service.watchBooks(WatchBooksRequest.getDefaultInstance());
        while (books.hasNext()) {
            printBook(books.next());
        }
    }

    // Viser et spørgsmål og læser svaret fra terminalen.
    private static String readInput(Scanner scanner, String question) {
        System.out.println(question);
        return scanner.nextLine().trim();
    }

    // Udskriver bogens oplysninger på én linje.
    private static void printBook(Book book) {
        System.out.printf("%d: %s (år: %d, forfatter-ID: %d, forlag-ID: %d)%n",
                book.getId(), book.getName(), book.getPublicationYear(), book.getAuthorId(), book.getPublisherId());
    }
}
