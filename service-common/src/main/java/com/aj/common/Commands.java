package com.aj.common;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import io.javalin.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;

/** Per-identity command keys, locked and stored in the domain transaction. */
public final class Commands {
    private Commands() {}
    public static String fingerprint(JsonNode n) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Json.write(canonical(n)).getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static Object canonical(JsonNode n) {
        if(n.isObject()) { var m=new TreeMap<String,Object>(); n.fields().forEachRemaining(e -> m.put(e.getKey(),canonical(e.getValue()))); return m; }
        if(n.isArray()) { var a=new ArrayList<>(); n.forEach(v -> a.add(canonical(v))); return a; }
        return n;
    }
    @SuppressWarnings("unchecked")
    public static Map<String,Object> run(Db db,Context ctx,JsonNode payload,Function<EntityManager,Map<String,Object>> action) {
        String key=ctx.header("Idempotency-Key");
        if(key==null) return db.tx(action);
        if(!key.matches("[A-Za-z0-9_-]{1,64}")) throw new BadRequestResponse("Ugyldig Idempotency-Key");
        String id=Access.principal(ctx).subject()+":"+ctx.path()+":"+key, hash=fingerprint(payload);
        return db.tx(em -> {
            em.createNativeQuery("select 1 from pg_advisory_xact_lock(hashtextextended(:id,0))").setParameter("id",id).getSingleResult();
            var rows=em.createNativeQuery("select fingerprint,response from command_keys where id=:id").setParameter("id",id).getResultList();
            if(!rows.isEmpty()) {
                Object[] row=(Object[])rows.get(0);
                if(!hash.equals(row[0])) throw new ConflictResponse("Samme retry-nøgle er brugt med andre oplysninger");
                ctx.header("Idempotency-Replayed","true");
                return Json.MAPPER.convertValue(Json.read((String)row[1]),Map.class);
            }
            Map<String,Object> response=action.apply(em);
            em.createNativeQuery("insert into command_keys(id,fingerprint,response,resource_guid) values(:id,:hash,:response,:guid)")
                .setParameter("id",id).setParameter("hash",hash).setParameter("response",Json.write(response))
                .setParameter("guid",UUID.fromString(response.get("guid").toString())).executeUpdate();
            return response;
        });
    }
}
