package com.aj.products.controller;
import com.aj.common.*;
import com.aj.products.entity.*;
import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.Javalin;
import io.javalin.http.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public class ProductController {
    private final Db db;
    public ProductController(Db db) { this.db=db; }
    public void registerRoutes(Javalin app) {
        app.post("/api/v1/products",ctx -> {
            JsonNode n=Json.read(ctx.body());
            var result=Commands.run(db,ctx,n,em -> { Product p=new Product(); p.setGuid(UUID.randomUUID()); ProductDetail d=new ProductDetail();
                fill(d,n); d.setCreatedAt(Instant.now()); d.setUpdatedAt(d.getCreatedAt()); p.setDetails(d); d.setProduct(p); em.persist(p); em.flush();
                publish(em,p,"product.created"); return view(p); });
            Telemetry.correlation(ctx,result.get("guid")); ctx.status(201).header("Location","/api/v1/products/"+result.get("guid")).json(result);
        });
        app.get("/api/v1/products",ctx -> { String status=ctx.queryParam("status");
            if(status!=null && !Set.of("AVAILABLE","UNAVAILABLE","RESERVED","SOLD").contains(status)) throw new BadRequestResponse("Ugyldig status");
            ctx.json(db.tx(em -> em.createQuery("select p from Product p where not exists (select d.id from ProductDeleted d where d.product=p) order by p.id",Product.class).getResultList().stream()
                .filter(p -> status==null || status.equals(p.getDetails().getStatus())).map(this::view).toList()));
        });
        app.get("/api/v1/products/{productGuid}",ctx -> { UUID g=Api.guid(ctx.pathParam("productGuid")); ctx.json(db.tx(em -> view(find(em,g,false)))); });
        app.put("/api/v1/products/{productGuid}",ctx -> {
            UUID g=Api.guid(ctx.pathParam("productGuid")); JsonNode n=Json.read(ctx.body());
            ctx.json(db.tx(em -> { Product p=find(em,g,true); ProductDetail d=p.getDetails();
                if(!com.aj.products.InventoryRules.editable(d.getStatus())) throw new ConflictResponse("En reserveret eller solgt bil kan ikke redigeres");
                fill(d,n); d.setUpdatedAt(Instant.now()); em.flush(); publish(em,p,"product.updated"); return view(p); }));
        });
        app.delete("/api/v1/products/{productGuid}",ctx -> {
            UUID g=Api.guid(ctx.pathParam("productGuid")); db.tx(em -> { Product p=find(em,g,true);
                if(Set.of("RESERVED","SOLD").contains(p.getDetails().getStatus())) throw new ConflictResponse("En reserveret eller solgt bil kan ikke slettes");
                em.persist(ProductDeleted.builder().product(p).deletedAt(Instant.now()).reason("Slettet via API").build());
                p.getDetails().setStatus("UNAVAILABLE"); p.getDetails().setUpdatedAt(Instant.now()); em.flush(); publish(em,p,"product.removed"); return null; }); ctx.status(204);
        });
    }
    private Product find(EntityManager em, UUID guid, boolean lock) {
        var q=em.createQuery("select p from Product p where p.guid=:g and not exists (select d.id from ProductDeleted d where d.product=p)",Product.class).setParameter("g",guid);
        if(lock) q.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        Product p=q.getResultStream().findFirst().orElseThrow(() -> new NotFoundResponse("Bilen blev ikke fundet"));
        if(lock) em.lock(p.getDetails(),LockModeType.PESSIMISTIC_WRITE); return p;
    }
    private int integer(JsonNode n,String f,int fallback) {
        if(!n.has(f)) return fallback;
        if(!n.get(f).isIntegralNumber() || !n.get(f).canConvertToInt() || n.get(f).asInt()<0) throw new BadRequestResponse("Ugyldigt felt: "+f);
        return n.get(f).asInt();
    }
    private BigDecimal decimal(JsonNode n,String f) {
        if(!n.has(f)) return BigDecimal.ZERO;
        if(!n.get(f).isNumber() || n.get(f).decimalValue().signum()<0) throw new BadRequestResponse("Ugyldigt felt: "+f);
        return n.get(f).decimalValue();
    }
    private List<String> strings(JsonNode n,String f) {
        if(!n.has(f)) return new ArrayList<>(); if(!n.get(f).isArray()) throw new BadRequestResponse("Ugyldigt felt: "+f);
        List<String> list=new ArrayList<>(); for(JsonNode v:n.get(f)) { if(!v.isTextual()) throw new BadRequestResponse("Ugyldigt felt: "+f); list.add(v.asText()); } return list;
    }
    private void fill(ProductDetail d,JsonNode n) {
        d.setBrand(Api.required(n,"brand")); d.setModel(Api.required(n,"model")); d.setYear(integer(n,"year",2026));
        if(d.getYear()<1886 || d.getYear()>java.time.Year.now().getValue()+1) throw new BadRequestResponse("Ugyldig årgang");
        d.setDescription(Api.text(n,"description")); d.setPrice(Api.money(n,"price")); d.setCurrency(Api.currency(n));
        d.setStatus(Api.choice(n,"status","AVAILABLE","AVAILABLE","UNAVAILABLE")); d.setMileage(integer(n,"mileage",0));
        d.setColor(Api.text(n,"color")); d.setFuelType(Api.text(n,"fuelType")); d.setTransmission(Api.text(n,"transmission"));
        JsonNode e=n.path("engine"), p=n.path("performance"), size=n.path("dimensions");
        d.setEngineType(Api.text(e,"type")); d.setPowerHp(integer(e,"powerHp",0)); d.setCapacityLiters(decimal(e,"capacityLiters"));
        d.setTopSpeedKmh(integer(p,"topSpeedKmh",0)); d.setZeroToHundredSeconds(decimal(p,"zeroTo100"));
        d.setLengthMm(integer(size,"length",0)); d.setWidthMm(integer(size,"width",0)); d.setHeightMm(integer(size,"height",0));
        d.setEquipment(strings(n,"equipment")); d.setImages(strings(n,"images"));
    }
    public Map<String,Object> view(Product p) {
        ProductDetail d=p.getDetails(); var m=new LinkedHashMap<String,Object>();
        m.put("guid",p.getGuid()); m.put("brand",d.getBrand()); m.put("model",d.getModel()); m.put("year",d.getYear()); m.put("description",d.getDescription());
        m.put("price",d.getPrice()); m.put("currency",d.getCurrency()); m.put("status",d.getStatus()); m.put("mileage",d.getMileage()); m.put("color",d.getColor());
        m.put("fuelType",d.getFuelType()); m.put("transmission",d.getTransmission());
        m.put("engine",Map.of("type",d.getEngineType(),"powerHp",d.getPowerHp(),"capacityLiters",d.getCapacityLiters()));
        m.put("performance",Map.of("topSpeedKmh",d.getTopSpeedKmh(),"zeroTo100",d.getZeroToHundredSeconds()));
        m.put("dimensions",Map.of("length",d.getLengthMm(),"width",d.getWidthMm(),"height",d.getHeightMm()));
        m.put("equipment",d.getEquipment()); m.put("images",d.getImages()); m.put("createdAt",d.getCreatedAt()); m.put("updatedAt",d.getUpdatedAt()); m.put("version",d.getVersion()); return m;
    }
    private void publish(EntityManager em,Product p,String topic) { Outbox.add(em,topic,view(p)); }
    public void event(EntityManager em,String topic,JsonNode n) {
        UUID productGuid=Api.guid(n,"productGuid"), orderGuid=Api.guid(n,"orderGuid"); Product p;
        em.createNativeQuery("select 1 from pg_advisory_xact_lock(hashtextextended(:id,0))").setParameter("id",orderGuid.toString()).getSingleResult();
        var previous=em.createNativeQuery("select state,product_guid from reservation_signals where order_guid=:o").setParameter("o",orderGuid).getResultList();
        String state=null;
        if(!previous.isEmpty()) {Object[] row=(Object[])previous.get(0);state=(String)row[0];if(!productGuid.equals(row[1]))throw new IllegalArgumentException("Order/car mismatch");}
        String next=com.aj.products.InventoryRules.signal(state,topic);
        em.createNativeQuery("insert into reservation_signals(order_guid,product_guid,state) values(:o,:p,:s) on conflict(order_guid) do update set state=:s,updated_at=now()")
            .setParameter("o",orderGuid).setParameter("p",productGuid).setParameter("s",next).executeUpdate();
        if(topic.equals("quantitycheck") && next.equals("RELEASED")){Outbox.add(em,"quantityrejected",n);return;}
        if(next.equals("PAID") && !topic.equals("quantitycheck") && !topic.equals("payment.completed"))return;
        try { p=find(em,productGuid,true); } catch(NotFoundResponse e) { if(topic.equals("quantitycheck")) Outbox.add(em,"quantityrejected",n); return; }
        ProductDetail d=p.getDetails(); String before=d.getStatus();
        if(topic.equals("quantitycheck")) {
            if(!com.aj.products.InventoryRules.canReserve(d.getStatus(),d.getReservedOrderGuid(),orderGuid) && !(next.equals("PAID") && orderGuid.equals(d.getReservedOrderGuid()))) { Outbox.add(em,"quantityrejected",n); return; }
            if(d.getStatus().equals("AVAILABLE")) {
                // Products sets the authoritative price while holding the reservation lock.
                // Legacy queued checks with a captured price must still reject a stale price.
                if(n.hasNonNull("totalAmount") && (d.getPrice().compareTo(Api.money(n,"totalAmount"))!=0 || !d.getCurrency().equals(Api.currency(n)))) { Outbox.add(em,"quantityrejected",n); return; }
                d.setStatus(next.equals("PAID") ? "SOLD" : "RESERVED"); d.setReservedOrderGuid(orderGuid); d.setUpdatedAt(Instant.now()); em.flush(); publish(em,p,"product.reserved");
            }
            var confirmed=n.deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode)confirmed).put("totalAmount",d.getPrice()).put("currency",d.getCurrency());
            Outbox.add(em,"quantityreserved",confirmed); Audit.status(em,p.getGuid(),before,d.getStatus());
        } else if(topic.equals("payment.completed") && d.getStatus().equals("AVAILABLE")) {
            d.setStatus("SOLD");d.setReservedOrderGuid(orderGuid);d.setUpdatedAt(Instant.now());em.flush();publish(em,p,"product.updated");Audit.status(em,p.getGuid(),before,d.getStatus());
        } else if(orderGuid.equals(d.getReservedOrderGuid()) && d.getStatus().equals("RESERVED")) {
            d.setStatus(topic.equals("payment.completed") ? "SOLD" : "AVAILABLE");
            if(!topic.equals("payment.completed")) d.setReservedOrderGuid(null);
            d.setUpdatedAt(Instant.now()); em.flush(); publish(em,p,"product.updated"); Audit.status(em,p.getGuid(),before,d.getStatus());
        }
    }
}
