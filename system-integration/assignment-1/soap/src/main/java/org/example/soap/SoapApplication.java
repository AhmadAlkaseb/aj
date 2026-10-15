package org.example.soap;

import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;

public final class SoapApplication {

    private SoapApplication() {
    }

    public static void main(String[] args) throws InterruptedException {
        // Factoryen opretter en CXF SOAP-server ud fra det genererede interface.
        JaxWsServerFactoryBean factory = new JaxWsServerFactoryBean();
        factory.setServiceClass(
                org.example.soap.library.service.LibraryPortType.class);
        // Serviceimplementeringen indeholder den konkrete forretningslogik.
        factory.setServiceBean(new LibraryServiceImpl());
        // Adressen skal svare til endpointet i WSDL-filen.
        factory.setAddress("http://localhost:8080/services/library");

        // Opret og start den indlejrede Jetty/CXF-server.
        Server server = factory.create();
        server.start();

        // WSDL'en bliver automatisk gjort tilgængelig med ?wsdl.
        System.out.println("Library SOAP service running at "
                + "http://localhost:8080/services/library");
        System.out.println("WSDL available at "
                + "http://localhost:8080/services/library?wsdl");
        System.out.println("Press Ctrl+C to stop.");
        Thread.currentThread().join();
    }
}
