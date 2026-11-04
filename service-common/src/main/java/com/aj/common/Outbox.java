package com.aj.common;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="event_outbox")
public class Outbox {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false,unique=true) public UUID eventId;
    @Column(nullable=false) public String topic;
    @Column(nullable=false,columnDefinition="text") public String payload;
    @Column(nullable=false) public Instant createdAt;
    public Outbox() {}
    public static void add(EntityManager em, String topic, Object payload) {
        Outbox e = new Outbox(); e.eventId = UUID.randomUUID(); e.topic = topic; e.createdAt = Instant.now();
        e.payload = Events.encode(e.eventId, topic, e.createdAt, payload); em.persist(e);
    }
}
