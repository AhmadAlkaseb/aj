package com.aj.common.demo;

import com.aj.common.Api;
import com.aj.common.Db;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Stable, fictional examination fixtures shared by independently started services. */
public final class DemoData {
    public static final int COUNT = 20;
    public static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 1, 10, 0);

    public record Car(String brand, String model, int year, String price, String fuelType,
                      int powerHp, String capacityLiters, String color) {
        public BigDecimal amount() { return new BigDecimal(price); }
    }

    public static final List<Car> CARS = List.of(
        new Car("Porsche", "911 Carrera", 2023, "1599900.00", "Benzin", 385, "3.0", "Sølv"),
        new Car("Audi", "RS e-tron GT", 2022, "999900.00", "El", 598, "0.0", "Grå"),
        new Car("BMW", "M4 Competition", 2023, "1199900.00", "Benzin", 510, "3.0", "Blå"),
        new Car("Mercedes-Benz", "AMG GT", 2021, "1399900.00", "Benzin", 530, "4.0", "Sort"),
        new Car("Ferrari", "Roma", 2022, "2899900.00", "Benzin", 620, "3.9", "Rød"),
        new Car("Bentley", "Continental GT", 2020, "2499900.00", "Benzin", 550, "4.0", "Grøn"),
        new Car("Aston Martin", "Vantage", 2021, "1899900.00", "Benzin", 510, "4.0", "Grøn"),
        new Car("Maserati", "Grecale", 2023, "899900.00", "Benzin", 300, "2.0", "Hvid"),
        new Car("Jaguar", "F-Type", 2020, "1099900.00", "Benzin", 450, "5.0", "Blå"),
        new Car("Lexus", "LC 500", 2021, "1299900.00", "Benzin", 464, "5.0", "Sølv"),
        new Car("Volvo", "EX30", 2024, "279900.00", "El", 272, "0.0", "Gul"),
        new Car("BMW", "i4 eDrive40", 2023, "529900.00", "El", 340, "0.0", "Blå"),
        new Car("Porsche", "Taycan", 2022, "859900.00", "El", 408, "0.0", "Hvid"),
        new Car("Audi", "e-tron 55", 2021, "499900.00", "El", 408, "0.0", "Sort"),
        new Car("Mercedes-Benz", "EQE 350", 2023, "699900.00", "El", 292, "0.0", "Sølv"),
        new Car("Tesla", "Model S", 2022, "659900.00", "El", 670, "0.0", "Hvid"),
        new Car("Polestar", "2 Long Range", 2023, "399900.00", "El", 299, "0.0", "Grå"),
        new Car("Volvo", "XC90 Recharge", 2022, "899900.00", "Hybrid", 455, "2.0", "Sort"),
        new Car("Land Rover", "Defender 110", 2023, "1199900.00", "Diesel", 300, "3.0", "Grøn"),
        new Car("Alfa Romeo", "Giulia Veloce", 2021, "549900.00", "Benzin", 280, "2.0", "Rød")
    );

    public static final List<String> FIRST_NAMES = List.of(
        "Anna", "Emil", "Sofie", "Oliver", "Freja", "William", "Ida", "Noah", "Clara", "Oscar",
        "Alma", "Lucas", "Ella", "Aksel", "Laura", "Victor", "Agnes", "Malthe", "Karla", "Arthur");
    public static final List<String> LAST_NAMES = List.of(
        "Jensen", "Nielsen", "Hansen", "Pedersen", "Andersen", "Christensen", "Larsen", "Sørensen",
        "Rasmussen", "Jørgensen", "Petersen", "Madsen", "Kristensen", "Olsen", "Thomsen", "Christiansen",
        "Poulsen", "Johansen", "Møller", "Mortensen");

    private DemoData() {}

    public static UUID guid(String domain, int index) {
        // Payments uses the same identity as the normal idempotent payment creation flow.
        String key = domain.equals("payments") ? "aj-payment:" + guid("orders", index)
                : "aj-demo-v1:" + domain + ":" + index;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    public static Car car(int index) { return CARS.get(index - 1); }
    public static String productStatus(int index) { return index <= 6 ? "SOLD" : index <= 10 ? "RESERVED" : "AVAILABLE"; }
    public static String orderStatus(int index) { return index <= 6 ? "COMPLETED" : index <= 10 ? "PAYMENT_PENDING" : "FAILED"; }
    public static String paymentStatus(int index) { return index <= 6 ? "COMPLETED" : index <= 10 ? "PENDING" : "FAILED"; }
    public static String email(int index) { return "kunde%02d@aj-demo.example".formatted(index); }
    public static String address(int index) { return "Demovej " + index + ", 2100 København Ø"; }

    /** Insert missing fixture identities only; preserve edits and soft-deleted records. */
    public static void seed(Db db, String entityName, String domain, BiConsumer<EntityManager, Integer> create) {
        if (!Boolean.parseBoolean(Api.env("DEMO_DATA_ENABLED", "true"))) return;
        int inserted = db.tx(em -> {
            // Multiple replicas starting at once must not race while inserting the same fixtures.
            em.createNativeQuery("select 1 from pg_advisory_xact_lock(74201)").getSingleResult();
            int count = 0;
            for (int index = 1; index <= COUNT; index++) {
                long existing = em.createQuery("select count(e) from " + entityName + " e where e.guid=:guid", Long.class)
                    .setParameter("guid", guid(domain, index)).getSingleResult();
                if (existing == 0) { create.accept(em, index); count++; }
            }
            return count;
        });
        org.slf4j.LoggerFactory.getLogger(DemoData.class).info("Demo data for {}: inserted {} missing records", domain, inserted);
    }
}
