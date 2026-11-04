package org.example.book;

import io.grpc.Server;
import io.grpc.ServerBuilder;

public final class BookServer {
    // Indlæser startdata og starter gRPC-serveren på port 50052.
    public static void main(String[] args) throws Exception {
        BookRepository repository = new BookRepository();
        Server server = ServerBuilder.forPort(50052)
                .addService(new BookServiceImpl(repository))
                .build()
                .start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdownNow));
        System.out.println("gRPC-server lytter på port 50052 med " + repository.findAll().size() + " bøger.");
        // Holder serverprogrammet kørende, indtil det stoppes.
        server.awaitTermination();
    }
}
