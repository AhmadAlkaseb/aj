package com.aj.common;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="event_inbox")
public class Inbox {
    @Id public String id;
    public Instant processedAt;
    public Inbox() {}
    public Inbox(String id) { this.id = id; this.processedAt = Instant.now(); }
}
