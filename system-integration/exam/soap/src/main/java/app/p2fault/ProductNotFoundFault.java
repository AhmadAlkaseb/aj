package app.p2fault;

import jakarta.xml.bind.annotation.*;
import jakarta.xml.ws.WebFault;

@WebFault(name = "ProductNotFoundFault", targetNamespace = "urn:products")
public class ProductNotFoundFault extends Exception {
    private final Detail faultInfo;
    
    public ProductNotFoundFault(String message, Detail faultInfo) {
        super(message);
        this.faultInfo = faultInfo;
    }
    public Detail getFaultInfo() { return faultInfo; }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "ProductNotFoundDetail")
    public static class Detail {
        public int productId;
        public Detail() { }
        public Detail(int productId) { this.productId = productId; }
    }
}
