package org.example.book;

import org.apache.dubbo.common.stream.StreamObserver;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;

import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BookClient {
    public static void main(String[] args) {
        BookService service;
        try {
            ReferenceConfig<BookService> reference = new ReferenceConfig<>();
            reference.setInterface(BookService.class);
            reference.setUrl("tri://localhost:50052");
            DubboBootstrap.getInstance().reference(reference).start();
            service = reference.get();
        } catch (RuntimeException exception) {
            System.out.println("Serveren er ikke tilgængelig.");
            return;
        }

        AtomicBoolean streaming = new AtomicBoolean();

        try (Scanner scanner = new Scanner(System.in)) {
            boolean running = true;
            while (running) {
                System.out.println("\n1. Opret en bog\n2. Find en bog\n3. Stream bøger\n4. Luk");
                System.out.print("Vælg: ");
                switch (scanner.nextLine()) {
                    case "1" -> createBook(scanner, service);
                    case "2" -> findBook(scanner, service);
                    case "3" -> startStream(service, streaming);
                    case "4" -> running = false;
                    default -> System.out.println("Ugyldigt valg.");
                }
            }
        } finally {
            streaming.set(false);
            DubboBootstrap.getInstance().stop();
        }
    }

    private static void createBook(Scanner scanner, BookService service) {
        System.out.print("Skriv bogtitel: ");
        String name = scanner.nextLine().trim();
        if (name.isBlank()) {
            System.out.println("Bogen skal have en titel.");
            return;
        }

        System.out.print("Skriv forfatter-ID: ");
        long authorId;
        try {
            authorId = Long.parseLong(scanner.nextLine().trim());
            if (authorId <= 0) {
                System.out.println("Forfatter-ID skal være større end 0.");
                return;
            }
        } catch (NumberFormatException exception) {
            System.out.println("Forfatter-ID skal være et helt tal.");
            return;
        }

        System.out.print("Skriv forlag-ID: ");
        long publisherId;
        try {
            publisherId = Long.parseLong(scanner.nextLine().trim());
            if (publisherId <= 0) {
                System.out.println("Forlag-ID skal være større end 0.");
                return;
            }
        } catch (NumberFormatException exception) {
            System.out.println("Forlag-ID skal være et helt tal.");
            return;
        }

        System.out.print("Skriv udgivelsesår: ");
        int publicationYear;
        try {
            publicationYear = Integer.parseInt(scanner.nextLine().trim());
            if (publicationYear <= 0) {
                System.out.println("Udgivelsesåret skal være større end 0.");
                return;
            }
        } catch (NumberFormatException exception) {
            System.out.println("Udgivelsesåret skal være et helt tal.");
            return;
        }

        try {
            Book book = service.createBook(CreateBookRequest.newBuilder()
                    .setName(name)
                    .setAuthorId(authorId)
                    .setPublisherId(publisherId)
                    .setPublicationYear(publicationYear)
                    .build()).getBook();
            System.out.println("Oprettet: " + book);
        } catch (RuntimeException exception) {
            System.out.println("Bogen kunne ikke oprettes.");
        }
    }

    private static void findBook(Scanner scanner, BookService service) {
        System.out.print("Skriv bog-ID: ");
        try {
            Book book = service.getBookById(GetBookByIdRequest.newBuilder()
                    .setBookId(Long.parseLong(scanner.nextLine()))
                    .build());
            System.out.println("Fundet: " + book);
        } catch (NumberFormatException exception) {
            System.out.println("ID skal være et tal.");
        } catch (RuntimeException exception) {
            System.out.println("Bogen blev ikke fundet.");
        }
    }

    private static void startStream(
            BookService service,
            AtomicBoolean streaming) {
        if (!streaming.compareAndSet(false, true)) {
            System.out.println("Streaming kører allerede.");
            return;
        }

        try {
            service.watchBooks(WatchBooksRequest.getDefaultInstance(), new StreamObserver<>() {
                @Override
                public void onNext(Book book) {
                    System.out.println("\nStream modtog: " + book);
                }

                @Override
                public void onError(Throwable throwable) {
                    streaming.set(false);
                    System.out.println("Streaming stoppet.");
                }

                @Override
                public void onCompleted() {
                    streaming.set(false);
                }
            });
            System.out.println("Streaming startet. Opret bøger manuelt med valg 1.");
        } catch (RuntimeException exception) {
            streaming.set(false);
            System.out.println("Streaming kunne ikke startes.");
        }
    }
}
