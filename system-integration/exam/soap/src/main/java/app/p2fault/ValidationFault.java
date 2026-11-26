package app.p2fault;

import jakarta.xml.bind.annotation.*;
import jakarta.xml.ws.WebFault;

@WebFault(name = "ValidationFault", targetNamespace = "urn:products")
public class ValidationFault extends Exception {
    private final Detail faultInfo;

    public ValidationFault(String message, Detail faultInfo) {
        super(message);
        this.faultInfo = faultInfo;
    }
    
    public Detail getFaultInfo() { return faultInfo; }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "ValidationDetail")
    public static class Detail {
        public String field;
        public Detail() { }
        public Detail(String field) { this.field = field; }
    }
}
