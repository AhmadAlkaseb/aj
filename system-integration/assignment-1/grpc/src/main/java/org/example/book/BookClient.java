package org.example.book;

import org.apache.dubbo.common.stream.StreamObserver;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;

import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        AtomicBoolean streaming = new AtomicBoolean();
        AtomicReference<ScheduledFuture<?>> task = new AtomicReference<>();

        try (Scanner scanner = new Scanner(System.in)) {
            boolean running = true;
            while (running) {
                System.out.println("\n1. Opret en bog\n2. Find en bog\n3. Stream bøger\n4. Luk");
                System.out.print("Vælg: ");
                switch (scanner.nextLine()) {
                    case "1" -> createBook(scanner, service);
                    case "2" -> findBook(scanner, service);
                    case "3" -> startStream(service, scheduler, streaming, task);
                    case "4" -> running = false;
                    default -> System.out.println("Ugyldigt valg.");
                }
            }
        } finally {
            stopStream(streaming, task);
            scheduler.shutdownNow();
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
            ScheduledExecutorService scheduler,
            AtomicBoolean streaming,
            AtomicReference<ScheduledFuture<?>> task) {
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
                    stopStream(streaming, task);
                    System.out.println("Streaming stoppet.");
                }

                @Override
                public void onCompleted() {
                    stopStream(streaming, task);
                }
            });

            task.set(scheduler.scheduleAtFixedRate(() -> {
                try {
                    createRandomBook(service);
                } catch (RuntimeException exception) {
                    stopStream(streaming, task);
                    System.out.println("Serveren er ikke tilgængelig.");
                }
            }, 20, 20, TimeUnit.SECONDS));
            System.out.println("Streaming startet. Ny tilfældig bog hvert 20. sekund.");
        } catch (RuntimeException exception) {
            stopStream(streaming, task);
            System.out.println("Streaming kunne ikke startes.");
        }
    }

    private static void createRandomBook(BookService service) {
        service.createBook(CreateBookRequest.newBuilder()
                .setName("Tilfældig bog " + ThreadLocalRandom.current().nextInt(1000, 10000))
                .setAuthorId(ThreadLocalRandom.current().nextLong(1, 10))
                .setPublisherId(ThreadLocalRandom.current().nextLong(1, 10))
                .setPublicationYear(ThreadLocalRandom.current().nextInt(1950, 2027))
                .build());
    }

    private static void stopStream(
            AtomicBoolean streaming,
            AtomicReference<ScheduledFuture<?>> task) {
        streaming.set(false);
        ScheduledFuture<?> currentTask = task.getAndSet(null);
        if (currentTask != null) {
            currentTask.cancel(false);
        }
    }
}
