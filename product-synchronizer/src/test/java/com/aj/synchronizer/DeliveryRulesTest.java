package com.aj.synchronizer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DeliveryRulesTest { @Test void enforcesBusinessBoundaries(){DeliveryRules.accepted(204);assertThrows(IllegalArgumentException.class,()->DeliveryRules.accepted(400));assertThrows(io.javalin.http.ServiceUnavailableResponse.class,()->DeliveryRules.accepted(401));assertThrows(io.javalin.http.ServiceUnavailableResponse.class,()->DeliveryRules.accepted(503));} }
