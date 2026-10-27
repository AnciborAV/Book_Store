package ru.aav.model;

import jakarta.persistence.*;
import java.util.Date;
import lombok.Getter;

@Getter
@MappedSuperclass
public abstract class BasicEntity extends Entity {

    @Temporal(TemporalType.TIMESTAMP)
    private Date modified = new Date();

    @PrePersist
    @PreUpdate
    protected void updateModifiedDate() {
        modified = new Date();
    }
}
