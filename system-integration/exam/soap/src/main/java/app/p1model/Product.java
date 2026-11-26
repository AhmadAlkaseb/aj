package app.p1model;

import jakarta.xml.bind.annotation.*;
import java.math.BigDecimal;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Product", propOrder = {"id", "name", "price"})
public class Product {
    public int id;
    public String name;
    public BigDecimal price;

    public Product() { }
    public Product(int id, String name, BigDecimal price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }
}
