package app;

import app.p4soap.ProductSoapService;

import jakarta.xml.ws.Endpoint;
import org.apache.cxf.Bus;
import org.apache.cxf.jaxws.EndpointImpl;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SoapApplication {
    public static void main(String[] args) { SpringApplication.run(SoapApplication.class, args); }

    @Bean
    Endpoint productsEndpoint(Bus bus, ProductSoapService service) {
        EndpointImpl endpoint = new EndpointImpl(bus, service);
        endpoint.publish("/products");
        return endpoint;
    }

    // http://localhost:8080/services/products?wsdl
}
