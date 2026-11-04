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
        when(ctx.method()).thenReturn(HandlerType.valueOf(method));
        return Application.target(ctx);
    }

    @Test
    void separatesProductReadsFromWrites() {
        assertEquals("http://product-view:7000", target("GET", "/api/v1/products/car-guid"));
        assertEquals("http://products:7000", target("POST", "/api/v1/products"));
        assertEquals("http://products:7000", target("DELETE", "/api/v1/products/car-guid"));
    }

    @Test
    void routesNestedResourcesToTheirOwningService() {
        assertEquals("http://orders:7000", target("GET", "/api/v1/users/user-guid/orders"));
        assertEquals("http://payments:7000", target("GET", "/api/v1/orders/order-guid/payments"));
        assertEquals("http://users:7000", target("GET", "/api/v1/users/user-guid"));
    }
}
