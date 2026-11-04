package com.aj.synchronizer;
import io.javalin.http.ServiceUnavailableResponse;
public final class DeliveryRules {
 private DeliveryRules() {}
 public static void accepted(int status) {
  if(status==400) throw new IllegalArgumentException("Invalid product event");
  if(status<200 || status>=300) throw new ServiceUnavailableResponse("Projection owner unavailable; retry required");
 }
}
