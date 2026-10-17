package ru.aav.model;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.Hibernate;
import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Getter
@MappedSuperclass
@Access(AccessType.FIELD)
public abstract class Entity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private int version;

    @Column(updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date created = new Date();

    @Override
    public final boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false;
        Entity entity = (Entity) other;
        return getId() != null && getId().equals(entity.getId());
    }

    @Override
    public final int hashCode() {
        // Must remain stable when the database assigns an id.
        return Hibernate.getClass(this).hashCode();
    }
}

