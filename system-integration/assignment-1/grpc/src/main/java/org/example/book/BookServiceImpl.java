package org.example.book;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.grpc.stub.ServerCallStreamObserver;

import java.util.ArrayList;
import java.util.List;

public final class BookServiceImpl extends BookServiceGrpc.BookServiceImplBase {
    private final BookRepository repository;
    private final List<ServerCallStreamObserver<Book>> subscribers = new ArrayList<>();

    // Bruger serverens fælles datalager.
    BookServiceImpl(BookRepository repository) {
        this.repository = repository;
    }

    // Registrerer klienten til fremtidige bøger.
    @Override
    public synchronized void watchBooks(WatchBooksRequest request, StreamObserver<Book> response) {
        ServerCallStreamObserver<Book> subscriber = (ServerCallStreamObserver<Book>) response;
        // Fjerner abonnementet, når klienten afbryder.
        subscriber.setOnCancelHandler(() -> removeSubscriber(subscriber));
        if (!subscriber.isCancelled()) {
            subscribers.add(subscriber);
            // repository.findAll().forEach(subscriber::onNext);
        }
        // Ingen onCompleted: streamen skal forblive åben til nye bøger.
    }

    // Fjerner en abonnent med samme lås som tilmelding og udsendelse.
    private synchronized void removeSubscriber(ServerCallStreamObserver<Book> subscriber) {
        subscribers.remove(subscriber);
    }

    // Returnerer bogen eller en gRPC-fejl, hvis ID er ugyldigt eller ukendt.
    @Override
    public void getBookById(GetBookByIdRequest request, StreamObserver<Book> response) {
        if (request.getBookId() <= 0) {
            response.onError(Status.INVALID_ARGUMENT.withDescription("Bog-ID skal være større end 0.")
                    .asRuntimeException());
            return;
        }
        Book book = repository.findById(request.getBookId());
        if (book == null) {
            response.onError(Status.NOT_FOUND.withDescription("Bog ikke fundet: " + request.getBookId())
                    .asRuntimeException());
            return;
        }
        response.onNext(book);
        response.onCompleted();
    }

    // Opretter bogen, giver abonnenter besked og svarer den klient, der oprettede den.
    // Samme lås som watchBooks sikrer rækkefølgen ved samtidige kald.
    @Override
    public synchronized void createBook(CreateBookRequest request, StreamObserver<CreateBookResponse> response) {
        if (request.getName().isBlank() || request.getAuthorId() <= 0
                || request.getPublisherId() <= 0 || request.getPublicationYear() <= 0) {
            response.onError(Status.INVALID_ARGUMENT
                    .withDescription("Titel skal udfyldes, og ID'er og udgivelsesår skal være større end 0.")
                    .asRuntimeException());
            return;
        }
        Book book = repository.create(request);

        var iterator = subscribers.iterator();
        while (iterator.hasNext()) {
            var subscriber = iterator.next();
            if (subscriber.isCancelled()) {
                iterator.remove();
                continue;
            }
            try {
                subscriber.onNext(book);
            } catch (RuntimeException exception) {
                iterator.remove();
            }
        }
        response.onNext(CreateBookResponse.newBuilder().setBook(book).build());
        response.onCompleted();
    }
}
