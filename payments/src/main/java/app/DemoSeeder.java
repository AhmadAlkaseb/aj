package app;

import com.aj.common.Db;
import com.aj.common.demo.DemoData;
import persistence.entities.Payment;
import persistence.entities.PaymentDetails;

public final class DemoSeeder {
    private DemoSeeder() {}
    public static void seed(Db db) {
        DemoData.seed(db, "Payment", "payments", (em, index) -> {
            Payment payment = new Payment();
            payment.setGuid(DemoData.guid("payments", index));
            var createdAt = DemoData.CREATED_AT.plusDays(index).plusMinutes(30);
            PaymentDetails details = PaymentDetails.builder()
                .customerGuid(DemoData.guid("users", index)).payment(payment).orderGuid(DemoData.guid("orders", index)).productGuid(DemoData.guid("products", index))
                .amount(DemoData.car(index).amount()).currency("DKK")
                .paymentMethod(index % 3 == 0 ? "CASH" : index % 3 == 1 ? "MANUAL" : "BANK_TRANSFER")
                .status(DemoData.paymentStatus(index)).transactionReference("DEMO-2026-%02d".formatted(index))
                .createdAt(createdAt).paidAt(index <= 6 ? createdAt.plusMinutes(30) : null)
                .build();
            payment.setDetails(details);
            em.persist(payment);
        });
    }
}
