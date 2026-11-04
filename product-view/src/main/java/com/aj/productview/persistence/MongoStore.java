package com.aj.productview.persistence;
import com.aj.common.Api;
import com.aj.common.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.client.*;
import com.mongodb.client.model.*;
import org.bson.Document;
import org.bson.types.Decimal128;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** The Product View-owned catalogue store. No other service includes this driver. */
public final class MongoStore implements AutoCloseable {
    private final MongoClient client;
    private final MongoDatabase database;
    private final MongoCollection<Document> products;
    private volatile boolean indexed;
    public MongoStore() {
        var settings=com.mongodb.MongoClientSettings.builder().applyConnectionString(new com.mongodb.ConnectionString(Api.env("MONGO_URI","mongodb://localhost:27018")))
            .applyToClusterSettings(b -> b.serverSelectionTimeout(2,TimeUnit.SECONDS)).applyToSocketSettings(b -> b.connectTimeout(2,TimeUnit.SECONDS).readTimeout(3,TimeUnit.SECONDS)).build();
        client=MongoClients.create(settings); database=client.getDatabase(Api.env("MONGO_DB","product_view")); products=database.getCollection("products");
    }
    public boolean healthy() { return database.runCommand(new Document("ping",1)).getDouble("ok")==1.0; }
    public void project(String eventId,String topic,JsonNode data) {
        String guid=Api.guid(data,"guid").toString(); long version=data.path("version").asLong();
        ensureIndexes();
        // Conditional replacement prevents a slow HTTP retry from overwriting a newer snapshot.
        for (int attempt=0; attempt<10; attempt++) {
            Document current=products.find(Filters.eq("guid",guid)).first();
            if(current!=null && com.aj.productview.ProjectionRules.obsolete(((Number)current.getOrDefault("version",-1L)).longValue(),version)) return;
            Document d=Document.parse(Json.write(data)); d.remove("_id");
            d.put("guid",guid); d.put("version",version); d.put("eventId",eventId); d.put("deleted",topic.equals("product.removed"));
            d.put("price",new Decimal128(data.path("price").decimalValue()));
            if(current==null) {
                try { products.insertOne(d); return; }
                catch(com.mongodb.MongoWriteException e) {
                    if(e.getError().getCategory()!=com.mongodb.ErrorCategory.DUPLICATE_KEY) throw e;
                }
            } else {
                var unchanged=current.containsKey("version") ? Filters.eq("version",current.get("version")) : Filters.exists("version",false);
                if(products.replaceOne(Filters.and(Filters.eq("guid",guid),unchanged),d).getMatchedCount()==1) return;
            }
        }
        throw new IllegalStateException("Projection contention; retry the event");
    }
    private synchronized void ensureIndexes() {
        if(indexed) return;
        products.createIndex(Indexes.ascending("guid"),new IndexOptions().unique(true));
        products.createIndex(Indexes.ascending("status"));
        indexed=true;
    }
    public List<Map<String,Object>> list(String status) {
        var filter=status==null ? Filters.ne("deleted",true) : Filters.and(Filters.ne("deleted",true),Filters.eq("status",status));
        List<Map<String,Object>> result=new ArrayList<>(); for(Document d:products.find(filter).sort(Sorts.descending("createdAt"))) result.add(view(d)); return result;
    }
    public Map<String,Object> search(Map<String,Object> input,Integer offset,Integer limit) {
        Map<String,Object> f=input==null ? Map.of() : input;
        int start=offset==null ? 0 : offset, size=limit==null ? 24 : limit;
        try { com.aj.productview.ProjectionRules.page(start,size); } catch(IllegalArgumentException ex) { throw new io.javalin.http.BadRequestResponse("Ugyldig sidegrænse"); }
        List<org.bson.conversions.Bson> filters=new ArrayList<>(); filters.add(Filters.ne("deleted",true));
        for(String key:List.of("brand","status")) if(f.get(key)!=null) {
            String value=f.get(key).toString();
            if(value.length()>100 || key.equals("status") && !Set.of("AVAILABLE","UNAVAILABLE","RESERVED","SOLD").contains(value)) throw new io.javalin.http.BadRequestResponse("Ugyldigt filter");
            filters.add(Filters.eq(key,value));
        }
        if(f.get("search")!=null) {
            String value=f.get("search").toString().trim(); if(value.length()>100) throw new io.javalin.http.BadRequestResponse("Søgningen er for lang");
            String regex=java.util.regex.Pattern.quote(value);
            filters.add(Filters.or(Filters.regex("brand",regex,"i"),Filters.regex("model",regex,"i")));
        }
        for(var e:Map.of("minPrice","price","maxPrice","price","minYear","year","maxYear","year","maxMileage","mileage").entrySet()) {
            if(f.get(e.getKey()) instanceof Number n) {
                double value=n.doubleValue(); if(!Double.isFinite(value) || value<0) throw new io.javalin.http.BadRequestResponse("Ugyldig filterværdi");
                Object bound=e.getValue().equals("price") ? new Decimal128(java.math.BigDecimal.valueOf(value)) : value;
                filters.add(e.getKey().startsWith("min") ? Filters.gte(e.getValue(),bound) : Filters.lte(e.getValue(),bound));
            }
        }
        var filter=Filters.and(filters); long total=products.countDocuments(filter);
        List<Map<String,Object>> items=new ArrayList<>();
        for(Document d:products.find(filter).sort(Sorts.orderBy(Sorts.descending("createdAt"),Sorts.ascending("guid"))).skip(start).limit(size)) items.add(view(d));
        return Map.of("items",items,"total",Math.toIntExact(total),"hasMore",start+items.size()<total);
    }
    public Map<String,Object> get(UUID guid) {
        Document d=products.find(Filters.and(Filters.eq("guid",guid.toString()),Filters.ne("deleted",true))).first();
        if(d==null) throw new io.javalin.http.NotFoundResponse("Bilen blev ikke fundet i kataloget"); return view(d);
    }
    private Map<String,Object> view(Document d) { Map<String,Object> m=new LinkedHashMap<>(d); m.remove("_id"); m.remove("eventId"); m.remove("deleted"); if(m.get("price") instanceof Decimal128 v) m.put("price",v.bigDecimalValue()); return m; }
    public void close() { client.close(); }
}
