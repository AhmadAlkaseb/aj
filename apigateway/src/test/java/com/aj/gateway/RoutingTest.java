package com.aj.gateway;

import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoutingTest {
    private String target(String method, String path) {
        Context ctx = mock(Context.class);
        when(ctx.path()).thenReturn(path);
        when(ctx.method()).thenReturn(HandlerType.findOrDefault(method));
        return Application.target(ctx);
    }

    @Test
    void separatesProductReadsFromWrites() {
        // Given
        String productPath = "/api/v1/products/car-guid";

        // When
        String readTarget = target("GET", productPath);
        String createTarget = target("POST", "/api/v1/products");
        String deleteTarget = target("DELETE", productPath);

        // Then
        assertEquals("http://product-view:7000", readTarget);
        assertEquals("http://products:7000", createTarget);
        assertEquals("http://products:7000", deleteTarget);
    }

    @Test
    void routesNestedResourcesToTheirOwningService() {
        // Given
        String customerOrdersPath = "/api/v1/users/user-guid/orders";
        String orderPaymentsPath = "/api/v1/orders/order-guid/payments";
        String customerPath = "/api/v1/users/user-guid";

        // When
        String ordersTarget = target("GET", customerOrdersPath);
        String paymentsTarget = target("GET", orderPaymentsPath);
        String usersTarget = target("GET", customerPath);

        // Then
        assertEquals("http://orders:7000", ordersTarget);
        assertEquals("http://payments:7000", paymentsTarget);
        assertEquals("http://users:7000", usersTarget);
    }
}
