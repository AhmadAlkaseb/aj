package persistence.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "authors")
public class Author {
    @Id
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Column(nullable = false)
    private String name;

    public Author(String name) {
        this(UUID.randomUUID(), name);
    }

    public Author(UUID id, String name) {
        this.id = id;
        this.name = name;
    }
}
