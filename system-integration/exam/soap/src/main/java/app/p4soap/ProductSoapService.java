package app.p4soap;

import app.p1model.Product;
import app.p3repository.ProductRepository;
import app.p2fault.ProductNotFoundFault;
import app.p2fault.ValidationFault;

import jakarta.jws.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
@WebService(serviceName = "ProductService", targetNamespace = "urn:products")
public class ProductSoapService {
    private final ProductRepository repository;

    public ProductSoapService(ProductRepository repository) { this.repository = repository; }

    @WebMethod
    public Product getProduct(@WebParam(name = "id") int id) throws ValidationFault, ProductNotFoundFault {
        validateId(id);
        return repository.find(id).orElseThrow(() -> notFound(id));
    }
    @WebMethod
    public List<Product> listProducts() { return repository.findAll(); }

    @WebMethod
    public Product createProduct(@WebParam(name = "name") String name,
                                 @WebParam(name = "price") BigDecimal price) throws ValidationFault {
        validateProduct(name, price);
        return repository.create(name.strip(), price);
    }
    @WebMethod
    public Product updateProduct(@WebParam(name = "id") int id,
                                 @WebParam(name = "name") String name,
                                 @WebParam(name = "price") BigDecimal price)
            throws ValidationFault, ProductNotFoundFault {
        validateId(id);
        validateProduct(name, price);
        if (!repository.update(id, name.strip(), price)) throw notFound(id);
        return new Product(id, name.strip(), price);
    }

    private void validateId(int id) throws ValidationFault {
        if (id <= 0) throw invalid("id", "Id skal være positivt.");
    }

    private void validateProduct(String name, BigDecimal price) throws ValidationFault {
        if (name == null || name.isBlank() || name.strip().length() > 100)
            throw invalid("name", "Navn skal indeholde 1-100 tegn.");
        
        // Afviser markup for at begrænse XSS-forsøg
        if (name.contains("<") || name.contains(">") || name.chars().anyMatch(Character::isISOControl))
            throw invalid("name", "Navn må ikke indeholde markup eller kontroltegn.");

        if (price == null || price.signum() < 0 || price.compareTo(new BigDecimal("99999999.99")) > 0
                || price.stripTrailingZeros().scale() > 2)
            throw invalid("price", "Pris skal være 0-99999999.99 med højst to decimaler.");
    }

    private ValidationFault invalid(String field, String message) {
        return new ValidationFault(message, new ValidationFault.Detail(field));
    }
    private ProductNotFoundFault notFound(int id) {
        return new ProductNotFoundFault("Produktet findes ikke.", new ProductNotFoundFault.Detail(id));
    }
}
