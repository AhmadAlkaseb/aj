package persistence.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "categories")
public class Category {
    @Id
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Column(nullable = false)
    private String name;

    public Category(String name) {
        this(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name);
    }

    public Category(UUID id, String name) {
        this.id = id;
        this.name = name;
    }
}
