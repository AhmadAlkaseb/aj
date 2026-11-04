package com.aj.products;

import com.aj.common.Db;
import com.aj.common.Outbox;
import com.aj.common.demo.DemoData;
import com.aj.products.controller.ProductController;
import com.aj.products.entity.Product;
import com.aj.products.entity.ProductDetail;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.List;

public final class DemoSeeder {
    private DemoSeeder() {}
    public static void seed(Db db, ProductController controller) {
        DemoData.seed(db, "Product", "products", (em, index) -> {
            var car = DemoData.car(index);
            var createdAt = DemoData.CREATED_AT.plusDays(index).toInstant(ZoneOffset.UTC);
            Product product = new Product();
            product.setGuid(DemoData.guid("products", index));
            ProductDetail details = ProductDetail.builder()
                .product(product).brand(car.brand()).model(car.model()).year(car.year())
                .description("Fiktiv eksamensbil. Pris og specifikationer er demonstrationsdata. "
                    + "Bilen har en komplet udstyrsliste og er klar til visning i AJ Motors.")
                .price(car.amount()).currency("DKK").status(DemoData.productStatus(index))
                .reservedOrderGuid(index <= 10 ? DemoData.guid("orders", index) : null)
                .mileage(6500 + index * 2300).color(car.color()).fuelType(car.fuelType())
                .transmission("Automatisk").engineType(car.fuelType()).powerHp(car.powerHp())
                .capacityLiters(new BigDecimal(car.capacityLiters())).topSpeedKmh(car.fuelType().equals("El") ? 200 : 250)
                .zeroToHundredSeconds(new BigDecimal("5.20")).lengthMm(4700).widthMm(1900).heightMm(1500)
                .equipment(List.of("Bakkamera", "Sædevarme", "Adaptiv fartpilot", "Navigation", "Parkeringssensorer"))
                .images(List.of()).createdAt(createdAt).updatedAt(createdAt)
                .build();
            product.setDetails(details);
            em.persist(product);
            em.flush();
            // Same snapshot contract and transactional outbox as ordinary product creation.
            Outbox.add(em, "product.created", controller.view(product));
        });
    }
}
