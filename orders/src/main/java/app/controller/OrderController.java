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

public class OrderController {
    private final Db db;
    public OrderController(Db db) { this.db=db; }
    public void register(Javalin app) {
        app.post("/api/v1/orders",this::create);
        app.get("/api/v1/orders",this::getAll);
        app.get("/api/v1/orders/{orderGuid}",this::getById);
        app.get("/api/v1/users/{userGuid}/orders",ctx -> {
            UUID user=Api.guid(ctx.pathParam("userGuid")); Access.owner(ctx,user);
            ctx.json(db.tx(em -> em.createQuery("select o from Order o where o.details.userGuid=:u and not exists (select d.id from OrderDeleted d where d.order=o) order by o.id desc",Order.class).setParameter("u",user).getResultList().stream().filter(o -> Access.admin(ctx) || Access.principal(ctx).customer().equals(o.getDetails().getUserGuid())).map(this::view).toList()));
        });
        app.get("/api/v1/orders/{orderGuid}/history",ctx -> {
            UUID g=Api.guid(ctx.pathParam("orderGuid")); ctx.json(db.tx(em -> { Order o=find(em,g,false);Access.owner(ctx,o.getDetails().getUserGuid());return Audit.history(em,g); }));
        });
        app.get("/api/v1/orders/{orderGuid}/confirmation",ctx -> {
            UUID g=Api.guid(ctx.pathParam("orderGuid"));
            byte[] pdf=db.tx(em -> {Order o=find(em,g,false); Access.owner(ctx,o.getDetails().getUserGuid());
                var docs=em.createNativeQuery("select document from order_confirmations where order_guid=:g").setParameter("g",g).getResultList();
                if(docs.isEmpty()) throw new NotFoundResponse("Ordrebekræftelsen er endnu ikke klar. Prøv igen om lidt.");return (byte[])docs.get(0);});
            ctx.contentType("application/pdf").header("Content-Disposition","attachment; filename=order-"+g+".pdf").result(pdf);
        });
        app.put("/internal/orders/{orderGuid}/confirmation",ctx -> {
            String configured=Api.env("CONFIRMATION_TOKEN","aj-confirmation-local");
            if(!java.security.MessageDigest.isEqual(configured.getBytes(java.nio.charset.StandardCharsets.UTF_8),Objects.toString(ctx.header("X-Confirmation-Token"),"").getBytes(java.nio.charset.StandardCharsets.UTF_8))) throw new UnauthorizedResponse();
            UUID g=Api.guid(ctx.pathParam("orderGuid")); byte[] bytes=ctx.bodyAsBytes();
            if(bytes.length<5 || bytes.length>262144 || !new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-")) throw new BadRequestResponse("Ugyldigt dokument");
            db.tx(em -> {Order o=find(em,g,true);if(!o.getDetails().getStatus().equals("COMPLETED")) throw new ConflictResponse("Ordren er ikke afsluttet");
                em.createNativeQuery("insert into order_confirmations(order_guid,document) values(:id,:doc) on conflict(order_guid) do nothing").setParameter("id",g).setParameter("doc",bytes).executeUpdate();return null;});ctx.status(204);
        });
        app.put("/api/v1/orders/{orderGuid}",this::update);
        app.delete("/api/v1/orders/{orderGuid}",this::delete);

    }
    private static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
    private Order find(EntityManager em,UUID g,boolean lock) {
        var q=em.createQuery("select o from Order o where o.guid=:g and not exists (select d.id from OrderDeleted d where d.order=o)",Order.class).setParameter("g",g);
        if(lock) q.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        return q.getResultStream().findFirst().orElseThrow(() -> new NotFoundResponse("Ordren blev ikke fundet"));
    }
    private Map<String,Object> view(Order o) {
        OrderDetails d=o.getDetails(); var m=new LinkedHashMap<String,Object>();
        m.put("guid",o.getGuid()); m.put("userGuid",d.getUserGuid()); m.put("productGuid",d.getProductGuid()); m.put("status",d.getStatus());
        m.put("totalAmount",d.getTotalAmount()); m.put("currency",d.getCurrency()); m.put("shippingAddress",d.getShippingAddress());
        m.put("createdAt",d.getCreatedAt()); m.put("updatedAt",d.getUpdatedAt()); m.put("notes",d.getNotes()); m.put("expiresAt",d.getExpiresAt()); return m;
    }
    private Map<String,Object> viewFor(Context ctx,Order o) { Access.owner(ctx,o.getDetails().getUserGuid()); return view(o); }
    public void maintenance() {
        db.tx(em -> {
            var pending=em.createQuery("from Order o where o.details.expiresAt is not null and o.details.expiresAt<=:now and o.details.status in ('PENDING','RESERVING','RESERVED','PAYMENT_PENDING','REVIEW_REQUIRED')",Order.class)
                .setParameter("now",now()).setMaxResults(50).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
            for(Order o:pending) {
                OrderDetails d=o.getDetails();
                if(app.OrderRules.expired(d.getStatus()).equals("CANCELLED")) {cancel(em,o);d.setExpiresAt(null);}
                else {
                    Audit.status(em,o.getGuid(),d.getStatus(),"REVIEW_REQUIRED"); d.setStatus("REVIEW_REQUIRED"); d.setExpiresAt(now().plusMinutes(5));
                    var request=message(o);request.put("paymentGuid",d.getPaymentGuid());Outbox.add(em,"payment.reconcile",request);
                }
            }
            return null;
        });
    }
    public void publishOwners() {
        db.tx(em -> { for(Order o:em.createQuery("from Order",Order.class).getResultList())
            Outbox.add(em,"order.owner",Map.of("orderGuid",o.getGuid(),"userGuid",o.getDetails().getUserGuid())); return null; });
    }
    private Map<String,Object> message(Order o) {
        OrderDetails d=o.getDetails(); var m=new LinkedHashMap<String,Object>(); m.put("orderGuid",o.getGuid()); m.put("productGuid",d.getProductGuid()); m.put("userGuid",d.getUserGuid()); m.put("totalAmount",d.getTotalAmount()); m.put("currency",d.getCurrency()); return m;
    }
    public void create(Context ctx) {
        JsonNode n=Json.read(ctx.body()); UUID user=Api.guid(n,"userGuid"), product=Api.guid(n,"productGuid");
        Access.owner(ctx,user);
        String address=Api.required(n,"shippingAddress");
        var result=Commands.run(db,ctx,n,em -> { Order o=new Order(); o.setGuid(UUID.randomUUID());
            OrderDetails d=new OrderDetails(); d.setOrder(o); o.setDetails(d); d.setUserGuid(user); d.setProductGuid(product);
            d.setStatus("PENDING"); d.setExpiresAt(now().plusMinutes(5));
            d.setShippingAddress(address); d.setNotes(Api.text(n,"notes")); d.setCreatedAt(now()); d.setUpdatedAt(d.getCreatedAt());
            em.persist(o); Audit.status(em,o.getGuid(),null,d.getStatus()); Outbox.add(em,"customer.check",message(o)); return view(o); });
        Telemetry.correlation(ctx,result.get("guid")); ctx.status(201).header("Location","/api/v1/orders/"+result.get("guid")).json(result);
    }
    public void getAll(Context ctx) { ctx.json(db.tx(em -> em.createQuery("select o from Order o where not exists (select d.id from OrderDeleted d where d.order=o) order by o.id desc",Order.class).getResultList().stream().filter(o -> Access.admin(ctx) || Access.principal(ctx).customer().equals(o.getDetails().getUserGuid())).map(this::view).toList())); }
    public void getById(Context ctx) { UUID g=Api.guid(ctx.pathParam("orderGuid")); ctx.json(db.tx(em -> viewFor(ctx,find(em,g,false)))); }
    public void update(Context ctx) {
        UUID g=Api.guid(ctx.pathParam("orderGuid")); JsonNode n=Json.read(ctx.body());
        ctx.json(db.tx(em -> { Order o=find(em,g,true); Access.owner(ctx,o.getDetails().getUserGuid()); OrderDetails d=o.getDetails();
            if(!app.OrderRules.editable(d.getStatus())) throw new ConflictResponse("Ordren kan ikke ændres i den aktuelle status");
            if(n.has("status")) { if(!"CANCELLED".equals(Api.required(n,"status"))) throw new BadRequestResponse("Kun annullering kan udføres manuelt"); cancel(em,o); }
            if(n.has("shippingAddress")) d.setShippingAddress(Api.required(n,"shippingAddress")); if(n.has("notes")) d.setNotes(Api.text(n,"notes"));
            d.setUpdatedAt(now()); return view(o); }));
    }
    private void cancel(EntityManager em,Order o) { Audit.status(em,o.getGuid(),o.getDetails().getStatus(),"CANCELLED"); o.getDetails().setStatus("CANCELLED"); o.getDetails().setUpdatedAt(now()); Outbox.add(em,"order.cancelled",message(o)); }
    public void delete(Context ctx) {
        UUID g=Api.guid(ctx.pathParam("orderGuid")); db.tx(em -> { Order o=find(em,g,true); Access.owner(ctx,o.getDetails().getUserGuid());
            if(Set.of("PAYMENT_PENDING","REVIEW_REQUIRED","COMPLETED").contains(o.getDetails().getStatus())) throw new ConflictResponse("Betaling er i gang eller afsluttet; ordren kan ikke slettes");
            if(Set.of("PENDING","RESERVING","RESERVED").contains(o.getDetails().getStatus())) cancel(em,o);
            em.persist(OrderDeleted.builder().order(o).deletedAt(now()).reason("Slettet via API").build()); return null; }); ctx.status(204);
    }
    public void event(EntityManager em,String topic,JsonNode n) {
        UUID g=Api.guid(n,"orderGuid"); Order o;
        if(topic.equals("payment.requested")) {
            UUID payment=Api.guid(n,"paymentGuid");
            try { o=find(em,g,true); } catch(NotFoundResponse ex) { Outbox.add(em,"payment.rejected",n); return; }
            OrderDetails d=o.getDetails();
            if(!app.OrderRules.claimable(d.getStatus(),d.getPaymentGuid(),payment)) {
                Outbox.add(em,"payment.rejected",n); return;
            }
            Audit.status(em,o.getGuid(),d.getStatus(),"PAYMENT_PENDING"); d.setPaymentGuid(payment); d.setStatus("PAYMENT_PENDING"); d.setExpiresAt(now().plusMinutes(30)); d.setUpdatedAt(now());
            var invoice=message(o); invoice.put("paymentGuid",payment); Outbox.add(em,"payment.authorized",invoice);
            var signals=em.createNativeQuery("select topic from payment_signals where order_guid=:o and payment_guid=:p").setParameter("o",g).setParameter("p",payment).getResultList();
            if(!signals.isEmpty()) {
                String terminal=signals.get(0).equals("payment.completed")?"COMPLETED":"FAILED";
                Audit.status(em,g,d.getStatus(),terminal);d.setStatus(terminal);
                if(terminal.equals("COMPLETED"))Outbox.add(em,"order.completed",invoice);
            }
            return;
        }
        try { o=find(em,g,true); } catch(NotFoundResponse ex) {
            // A soft-deleted/cancelled order must still release a late reservation.
            if(topic.equals("quantityreserved")) Outbox.add(em,"order.cancelled",n);
            return;
        }
        if(Set.of("payment.completed","paymentfail").contains(topic)) {
            UUID payment=Api.guid(n,"paymentGuid");
            UUID expected=UUID.nameUUIDFromBytes(("aj-payment:"+g).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if(!expected.equals(payment))throw new IllegalArgumentException("Unexpected payment identity");
            em.createNativeQuery("insert into payment_signals(order_guid,payment_guid,topic) values(:o,:p,:t) on conflict(order_guid) do nothing")
                .setParameter("o",g).setParameter("p",payment).setParameter("t",topic).executeUpdate();
        }
        OrderDetails d=o.getDetails(); String before=d.getStatus();
        if(topic.equals("customer.valid")) {
            if(d.getStatus().equals("PENDING") && d.getUserGuid().equals(Api.guid(n,"userGuid"))) {
                d.setStatus("RESERVING"); Outbox.add(em,"quantitycheck",message(o));
            }
        } else if(topic.equals("customer.invalid") && d.getStatus().equals("PENDING")) d.setStatus("REJECTED");
        else if(topic.equals("quantityreserved")) {
            if(Set.of("PENDING","RESERVING").contains(d.getStatus())) {
                d.setTotalAmount(Api.money(n,"totalAmount")); d.setCurrency(Api.currency(n)); d.setStatus("RESERVED"); d.setExpiresAt(now().plusMinutes(30));
            }
            else if(d.getStatus().equals("CANCELLED")) Outbox.add(em,"order.cancelled",message(o));
        } else if(topic.equals("quantityrejected") && Set.of("PENDING","RESERVING").contains(d.getStatus())) d.setStatus("REJECTED");
        else if(Set.of("payment.completed","paymentfail").contains(topic) && Set.of("PAYMENT_PENDING","REVIEW_REQUIRED").contains(d.getStatus()) && Objects.equals(d.getPaymentGuid(),Api.guid(n,"paymentGuid")))
            d.setStatus(topic.equals("payment.completed") ? "COMPLETED" : "FAILED");
        d.setUpdatedAt(now());
        Audit.status(em,o.getGuid(),before,d.getStatus());
        if(!before.equals(d.getStatus()) && d.getStatus().equals("COMPLETED")) {
            var confirmation=message(o); confirmation.put("paymentGuid",d.getPaymentGuid()); Outbox.add(em,"order.completed",confirmation);
        }
    }
}
