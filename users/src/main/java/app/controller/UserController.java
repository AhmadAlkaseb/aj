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
public class UserController {
    private final Db db;
    public UserController(Db db) { this.db=db; }
    public void register(Javalin app) {
        app.post("/api/v1/users", this::create);
        app.get("/api/v1/users", this::getAll);
        app.post("/api/v1/users/me", this::ensureProfile);
        app.get("/api/v1/users/{userGuid}", this::getById);
        app.put("/api/v1/users/{userGuid}", this::update);
        app.delete("/api/v1/users/{userGuid}", this::delete);
    }
    private User find(EntityManager em, UUID guid) {
        return em.createQuery("select u from User u where u.guid=:g and not exists (select d.id from UserDeleted d where d.user=u)",User.class)
            .setParameter("g",guid).getResultStream().findFirst().orElseThrow(() -> new NotFoundResponse("Brugeren blev ikke fundet"));
    }
    private void fill(UserDetails d, JsonNode n) {
        d.setFirstName(Api.required(n,"firstName")); d.setLastName(Api.required(n,"lastName"));
        String email=app.CustomerRules.email(Api.required(n,"email"));
        d.setEmail(email); d.setPhoneNumber(Api.text(n,"phoneNumber")); d.setAddress(Api.text(n,"address"));
        d.setPostalCode(Api.text(n,"postalCode")); d.setCity(Api.text(n,"city"));
    }
    private Map<String,Object> view(User u) {
        UserDetails d=u.getDetails(); var n=new LinkedHashMap<String,Object>();
        n.put("guid",u.getGuid()); n.put("firstName",d.getFirstName()); n.put("lastName",d.getLastName()); n.put("email",d.getEmail());
        n.put("phoneNumber",d.getPhoneNumber()); n.put("address",d.getAddress()); n.put("postalCode",d.getPostalCode()); n.put("city",d.getCity()); return n;
    }
    public void getAll(Context ctx) { ctx.json(db.tx(em -> em.createQuery("select u from User u where not exists (select d.id from UserDeleted d where d.user=u) order by u.id",User.class).getResultList().stream().filter(u -> Access.admin(ctx) || Access.principal(ctx).customer().equals(u.getGuid())).map(this::view).toList())); }
    public void getById(Context ctx) { UUID g=Api.guid(ctx.pathParam("userGuid")); Access.owner(ctx,g); ctx.json(db.tx(em -> view(find(em,g)))); }
    public void create(Context ctx) {
        JsonNode n=Json.read(ctx.body());
        var result=Commands.run(db,ctx,n,em -> { User u=new User(); u.setGuid(UUID.randomUUID()); UserDetails d=new UserDetails(); fill(d,n);
            d.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC)); d.setUser(u); u.setDetails(d); em.persist(u); em.flush(); return view(u); });
        Telemetry.correlation(ctx,result.get("guid")); ctx.status(201).header("Location","/api/v1/users/"+result.get("guid")).json(result);
    }
    public void update(Context ctx) {
        UUID g=Api.guid(ctx.pathParam("userGuid")); Access.owner(ctx,g); JsonNode n=Json.read(ctx.body());
        db.tx(em -> { User u=find(em,g); fill(u.getDetails(),n); return null; });
        ctx.json(Map.of("guid",g,"updatedAt",Instant.now()));
    }
    public void delete(Context ctx) {
        UUID g=Api.guid(ctx.pathParam("userGuid")); Access.owner(ctx,g);
        db.tx(em -> { User u=find(em,g); em.lock(u,LockModeType.PESSIMISTIC_WRITE); em.persist(UserDeleted.builder().user(u).deletedAt(LocalDateTime.now(ZoneOffset.UTC)).reason("Slettet via API").build()); return null; }); ctx.status(204);
    }
    public void ensureProfile(Context ctx) {
        UUID guid=Access.principal(ctx).customer();
        com.nimbusds.jwt.JWTClaimsSet claims=ctx.attribute("claims");
        ctx.json(db.tx(em -> {
            var existing=em.createQuery("from User where guid=:g",User.class).setParameter("g",guid).getResultStream().findFirst();
            if(existing.isPresent()) return view(find(em,guid));
            try {
                User user=new User(); user.setGuid(guid);
                UserDetails d=new UserDetails(); d.setUser(user); user.setDetails(d);
                d.setFirstName(claims.getStringClaim("given_name")); d.setLastName(claims.getStringClaim("family_name"));
                String email=claims.getStringClaim("email");
                if(email==null || email.isBlank()) throw new BadRequestResponse("Login mangler e-mail");
                d.setEmail(email.toLowerCase(Locale.ROOT)); d.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
                em.persist(user); em.flush(); return view(user);
            } catch(java.text.ParseException ex) { throw new UnauthorizedResponse("Ugyldig identitet"); }
        }));
    }
    public void event(EntityManager em,String topic,JsonNode n) {
        Api.guid(n,"orderGuid"); UUID user=Api.guid(n,"userGuid");
        boolean valid;
        try { find(em,user); valid=true; } catch(NotFoundResponse ex) { valid=false; }
        Outbox.add(em,valid ? "customer.valid" : "customer.invalid",n);
    }
}
