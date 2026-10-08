package org.example.book;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.common.constants.CommonConstants;

public final class BookServer {
    public static void main(String[] args) {
        // Opretter Dubbo-konfigurationen for den service, der skal udstilles.
        ServiceConfig<BookService> service = new ServiceConfig<>();
        // Angiver det interface, som klienterne bruger til at kalde RPC-metoderne.
        service.setInterface(BookService.class);
        // Angiver den konkrete klasse, som håndterer klienternes kald.
        service.setRef(new BookServiceImpl());

        // Navngiver Dubbo-applikationen.
        ApplicationConfig application = new ApplicationConfig("book-server");

        // Starter Triple-serveren på port 50052 og holder processen kørende.
        DubboBootstrap.getInstance() // Henter Dubbo-serverens bootstrap.
            .application(application) // Tilknytter applikationsnavnet.
            .protocol(new ProtocolConfig(CommonConstants.TRIPLE, 50052)) // Bruger Triple på port 50052.
            .service(service) // Udstiller bogservicen.
            .start() // Starter serveren.
            .await(); // Holder serverprocessen kørende.
    }
}
