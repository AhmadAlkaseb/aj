package org.example.book;

import org.apache.dubbo.common.stream.StreamObserver;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class BookServiceImpl extends DubboBookServiceTriple.BookServiceImplBase {
    // Repository og aktive abonnenter lever så længe serveren kører.
    private final BookRepository repository = new BookRepository();
    private final List<StreamObserver<Book>> subscribers = new CopyOnWriteArrayList<>();

    // Returnerer bogen med det ønskede ID.
    @Override
    public Book getBookById(GetBookByIdRequest request) {
        Book book = repository.findById(request.getBookId());
        if (book == null) {
            throw new IllegalArgumentException("Bog ikke fundet: " + request.getBookId());
        }
        return book;
    }

    // Opretter en bog og sender den videre til alle aktive abonnenter.
    @Override
    public CreateBookResponse createBook(CreateBookRequest request) {
        Book book = repository.create(request);
        notifySubscribers(book);
        return CreateBookResponse.newBuilder()
                .setBook(book)
                .build();
    }

    // Tilmelder klienten til beskeder om nye bøger.
    @Override
    public void watchBooks(WatchBooksRequest request, StreamObserver<Book> responseObserver) {
        subscribers.add(responseObserver);
    }

    // Sender en ny bog til alle abonnenter og fjerner dem, der fejler.
    private void notifySubscribers(Book book) {
        for (StreamObserver<Book> subscriber : subscribers) {
            try {
                subscriber.onNext(book);
            } catch (RuntimeException exception) {
                subscribers.remove(subscriber);
                subscriber.onError(exception);
            }
        }
    }
}
