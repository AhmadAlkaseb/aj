package com.aj.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.rabbitmq.client.*;
import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** One channel and one worker per process; SQL changes and outgoing events commit together. */
public final class Bus implements AutoCloseable {
    public interface Handler { void accept(EntityManager em, String topic, JsonNode data); }
    public interface Projection { void accept(String eventId, String topic, JsonNode data); }
    private final Db db;
    private final String queue;
    private final Handler handler;
    private final Projection projection;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private Connection connection;
    private Channel channel;
    private volatile boolean healthy;
    private String lastFailure;
    public Bus(Db db, String queue, Handler handler) { this(db, queue, handler, null); }
    public Bus(String queue, Projection projection) { this(null, queue, null, projection); }
    private Bus(Db db, String queue, Handler handler, Projection projection) {
        this.db=db; this.queue=queue; this.handler=handler; this.projection=projection;
        worker.scheduleWithFixedDelay(this::tick, 0, 350, TimeUnit.MILLISECONDS);
    }
    private void connect() throws Exception {
        if (channel != null && channel.isOpen() && connection.isOpen()) return;
        disconnect();
        ConnectionFactory f = new ConnectionFactory(); f.setUri(Api.env("RABBITMQ_URL", "amqp://aj:aj-local@localhost:5672/%2f"));
        f.setConnectionTimeout(2000); f.setHandshakeTimeout(3000); f.setAutomaticRecoveryEnabled(false);
        connection=f.newConnection(); channel=connection.createChannel(); channel.confirmSelect(); topology(channel);
    }
    public static void topology(Channel c) throws Exception {
        c.exchangeDeclare("aj.events", "topic", true); c.exchangeDeclare("aj.dead", "fanout", true);
        c.queueDeclare("aj.dead", true, false, false, null); c.queueBind("aj.dead", "aj.dead", "");
        Map<String,List<String>> bindings=Map.of(
            "aj.products", List.of("quantitycheck", "order.cancelled", "paymentfail", "payment.completed"),
            "aj.users", List.of("customer.check"),
            "aj.orders", List.of("customer.valid", "customer.invalid", "quantityreserved", "quantityrejected", "payment.requested", "paymentfail", "payment.completed"),
            "aj.payments", List.of("payment.authorized", "payment.rejected", "order.owner", "payment.reconcile"),
            "aj.product-sync", List.of("product.#"),
            "aj.confirmations", List.of("order.completed"));
        for (var b:bindings.entrySet()) {
            c.queueDeclare(b.getKey(), true, false, false, Map.of("x-dead-letter-exchange", "aj.dead"));
            for (String topic:b.getValue()) c.queueBind(b.getKey(), "aj.events", topic);
        }
    }
    private void tick() {
        if (Thread.currentThread().isInterrupted()) return;
        try {
            if(db!=null) db.tx(em -> {
                Object[] metrics=(Object[])em.createNativeQuery("select count(*),coalesce(extract(epoch from now()-min(createdat)),0) from event_outbox").getSingleResult();
                Telemetry.gauge("outbox_pending",((Number)metrics[0]).doubleValue());Telemetry.gauge("outbox_oldest_seconds",((Number)metrics[1]).doubleValue());
                return null;
            });
            connect();
            if (db != null) db.tx(em -> {
                var events=em.createQuery("from Outbox order by id", Outbox.class).setMaxResults(25).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
                for (Outbox e: events) {
                    try {
                        channel.basicPublish("aj.events", e.topic, new AMQP.BasicProperties.Builder().deliveryMode(2).contentType("application/json").messageId(e.eventId.toString()).build(), e.payload.getBytes(StandardCharsets.UTF_8));
                        channel.waitForConfirmsOrDie(3000); em.remove(e);
                    } catch (Exception ex) { throw new IllegalStateException(ex); }
                }
                return null;
            });
            if (queue != null) for (int i=0; i<20; i++) {
                GetResponse msg=channel.basicGet(queue, false); if(msg==null) break;
                String id=msg.getProps().getMessageId(), topic=msg.getEnvelope().getRoutingKey();
                try {
                    if (id==null) throw new IllegalArgumentException("Missing event ID");
                    Events.Decoded event=Events.decode(id, topic, Json.read(new String(msg.getBody(), StandardCharsets.UTF_8)));
                    Events.process(event, () -> {
                        if (projection != null) projection.accept(id, topic, event.payload());
                        else db.tx(em -> { if(em.find(Inbox.class,id)==null) { handler.accept(em,topic,event.payload()); em.persist(new Inbox(id)); } return null; });
                    });
                    if(projection!=null)Telemetry.gauge("projection_lag_seconds",Math.max(0,java.time.Duration.between(event.occurredAt(),java.time.Instant.now()).toMillis()/1000.0));
                    Telemetry.log(Map.of("event","broker.processed","eventId",id,"topic",topic,"correlationId",event.correlationId(),"queue",queue));
                    channel.basicAck(msg.getEnvelope().getDeliveryTag(), false);
                } catch (IllegalArgumentException | io.javalin.http.BadRequestResponse malformed) {
                    org.slf4j.LoggerFactory.getLogger(Bus.class).error("Invalid event {}",id);
                    channel.basicReject(msg.getEnvelope().getDeliveryTag(),false);
                } catch (Exception ex) { channel.basicNack(msg.getEnvelope().getDeliveryTag(),false,true); throw ex; }
            }
            healthy=true; lastFailure=null;
        } catch (Exception e) {
            String failure=e.getClass().getSimpleName();
            if(!Objects.equals(lastFailure,failure)) org.slf4j.LoggerFactory.getLogger(Bus.class).warn("Event worker will retry: {}",failure);
            lastFailure=failure; healthy=false; disconnect();
        }
    }
    public boolean healthy() { return healthy; }
    private void disconnect() { try { if(connection!=null) connection.close(); } catch(Exception failure) { org.slf4j.LoggerFactory.getLogger(Bus.class).debug("Broker close failed: {}",failure.getClass().getSimpleName()); } channel=null; connection=null; }
    public void close() { worker.shutdownNow(); try { worker.awaitTermination(5,TimeUnit.SECONDS); } catch(InterruptedException e) { Thread.currentThread().interrupt(); } disconnect(); }
}
