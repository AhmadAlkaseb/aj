package app.controller;
import com.aj.common.*;
import com.aj.common.Access;
import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.Javalin;
import io.javalin.http.*;
import jakarta.persistence.*;
import persistence.entities.*;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

public class PaymentController {
    private final Db db;
    public PaymentController(Db db) { this.db=db; }
    public void register(Javalin app) {
        app.post("/api/v1/payments",this::create);
        app.get("/api/v1/payments",this::getAll);
        app.get("/api/v1/payments/{paymentGuid}",this::getById);
        app.get("/api/v1/orders/{orderGuid}/payments",ctx -> {
            UUID order=Api.guid(ctx.pathParam("orderGuid"));
            ctx.json(db.tx(em -> em.createQuery("select p from Payment p where p.details.orderGuid=:o order by p.id desc",Payment.class).setParameter("o",order).getResultList().stream().filter(p -> Access.admin(ctx) || Access.principal(ctx).customer().equals(p.getDetails().getCustomerGuid())).map(this::view).toList()));
        });
        app.put("/api/v1/payments/{paymentGuid}",this::update);
    }
    private Payment find(EntityManager em,UUID g,boolean lock) {
        var q=em.createQuery("select p from Payment p where p.guid=:g",Payment.class).setParameter("g",g); if(lock) q.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        return q.getResultStream().findFirst().orElseThrow(() -> new NotFoundResponse("Betalingen blev ikke fundet"));
    }
    private Map<String,Object> view(Payment p) {
        PaymentDetails d=p.getDetails(); var m=new LinkedHashMap<String,Object>();
        m.put("guid",p.getGuid()); m.put("orderGuid",d.getOrderGuid()); m.put("amount",d.getAmount()); m.put("currency",d.getCurrency());
        m.put("paymentMethod",d.getPaymentMethod()); m.put("status",d.getStatus()); m.put("transactionReference",d.getTransactionReference());
        m.put("paidAt",d.getPaidAt()); m.put("createdAt",d.getCreatedAt()); return m;
    }
    private Map<String,Object> viewFor(Context ctx,Payment p) { Access.owner(ctx,p.getDetails().getCustomerGuid()); return view(p); }
    public void create(Context ctx) {
        JsonNode n=Json.read(ctx.body()); UUID order=Api.guid(n,"orderGuid");
        String method=Api.choice(n,"paymentMethod","MANUAL","MANUAL","BANK_TRANSFER","CASH");
        // Stable identity makes retry after a lost response safe, including a lost claim response.
        UUID guid=UUID.nameUUIDFromBytes(("aj-payment:"+order).getBytes(StandardCharsets.UTF_8));
        var existing=db.tx(em -> em.createQuery("select p from Payment p where p.guid=:g",Payment.class).setParameter("g",guid).getResultStream().findFirst().map(this::view).orElse(null));
        if(existing!=null) { ctx.status(200).json(existing); return; }
        var result=Commands.run(db,ctx,n,em -> { Payment p=new Payment(); p.setGuid(guid); PaymentDetails d=new PaymentDetails(); d.setPayment(p); p.setDetails(d);
            d.setOrderGuid(order); Audit.status(em,guid,null,"REQUESTED");
            d.setPaymentMethod(method); d.setStatus("REQUESTED"); d.setTransactionReference("MANUAL-"+guid); d.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC)); em.persist(p); Outbox.add(em,"payment.requested",Map.of("orderGuid",order,"paymentGuid",guid)); return view(p); });
        Telemetry.correlation(ctx,order); ctx.status(201).header("Location","/api/v1/payments/"+guid).json(result);
    }
    public void getAll(Context ctx) { ctx.json(db.tx(em -> em.createQuery("from Payment order by id desc",Payment.class).getResultList().stream().filter(p -> Access.admin(ctx) || Access.principal(ctx).customer().equals(p.getDetails().getCustomerGuid())).map(this::view).toList())); }
    public void getById(Context ctx) { UUID g=Api.guid(ctx.pathParam("paymentGuid")); ctx.json(db.tx(em -> viewFor(ctx,find(em,g,false)))); }
    public void update(Context ctx) {
        UUID g=Api.guid(ctx.pathParam("paymentGuid")); JsonNode n=Json.read(ctx.body()); String status=Api.choice(n,"status",null,"COMPLETED","FAILED");
        ctx.json(db.tx(em -> { Payment p=find(em,g,true); PaymentDetails d=p.getDetails();
            app.PaymentRules.allow(d.getStatus(),status);
            if(status.equals(d.getStatus())) return view(p);
            if(!d.getStatus().equals("PENDING")) throw new ConflictResponse("Betalingen afventer godkendelse eller er afsluttet");
            Audit.status(em,p.getGuid(),d.getStatus(),status); d.setStatus(status); if(status.equals("COMPLETED")) d.setPaidAt(LocalDateTime.now(ZoneOffset.UTC));
            Outbox.add(em,status.equals("COMPLETED") ? "payment.completed" : "paymentfail",Map.of("paymentGuid",p.getGuid(),"orderGuid",d.getOrderGuid(),"productGuid",d.getProductGuid())); return view(p); }));
    }
    public void event(EntityManager em,String topic,JsonNode n) {
        if(topic.equals("payment.reconcile")) {
            UUID guid=Api.guid(n,"paymentGuid");
            var found=em.createQuery("from Payment where guid=:g",Payment.class).setParameter("g",guid).getResultStream().findFirst();
            if(found.isPresent()) {
                Payment p=found.get();PaymentDetails d=p.getDetails();
                if(Set.of("COMPLETED","FAILED").contains(d.getStatus())) Outbox.add(em,d.getStatus().equals("COMPLETED")?"payment.completed":"paymentfail",Map.of("paymentGuid",guid,"orderGuid",d.getOrderGuid(),"productGuid",d.getProductGuid()));
            }
            return;
        }
        if(topic.equals("order.owner")) {
            UUID order=Api.guid(n,"orderGuid"), customer=Api.guid(n,"userGuid");
            em.createQuery("update PaymentDetails set customerGuid=:c where orderGuid=:o and customerGuid is null").setParameter("c",customer).setParameter("o",order).executeUpdate(); return;
        }
        UUID payment=Api.guid(n,"paymentGuid"), order=Api.guid(n,"orderGuid");
        Payment p=find(em,payment,true); PaymentDetails d=p.getDetails();
        if(!order.equals(d.getOrderGuid())) throw new IllegalArgumentException("Payment/order mismatch");
        if(!"REQUESTED".equals(d.getStatus())) return;
        String before=d.getStatus();
        if(topic.equals("payment.authorized")) {
            d.setCustomerGuid(Api.guid(n,"userGuid")); d.setProductGuid(Api.guid(n,"productGuid")); d.setAmount(Api.money(n,"totalAmount")); d.setCurrency(Api.currency(n)); d.setStatus("PENDING");
        } else if(topic.equals("payment.rejected")) d.setStatus("REJECTED");
        Audit.status(em,p.getGuid(),before,d.getStatus());
    }
}
