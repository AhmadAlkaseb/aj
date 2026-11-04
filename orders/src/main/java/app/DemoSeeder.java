package app;

import com.aj.common.Db;
import com.aj.common.demo.DemoData;
import persistence.entities.Order;
import persistence.entities.OrderDetails;

public final class DemoSeeder {
    private DemoSeeder() {}
    public static void seed(Db db) {
        DemoData.seed(db, "Order", "orders", (em, index) -> {
            Order order = new Order();
            order.setGuid(DemoData.guid("orders", index));
            OrderDetails details = OrderDetails.builder()
                .order(order).userGuid(DemoData.guid("users", index)).productGuid(DemoData.guid("products", index))
                .paymentGuid(DemoData.guid("payments", index)).status(DemoData.orderStatus(index))
                .totalAmount(DemoData.car(index).amount()).currency("DKK").shippingAddress(DemoData.address(index))
                .createdAt(DemoData.CREATED_AT.plusDays(index)).updatedAt(DemoData.CREATED_AT.plusDays(index).plusHours(1))
                .notes("Fiktiv historisk eksamensordre " + index + ". Betaling registreres manuelt.")
                .build();
            order.setDetails(details);
            em.persist(order);
        });
    }
}
