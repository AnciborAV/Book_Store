package ru.aav.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;


@Getter
@Setter
@NoArgsConstructor
@MappedSuperclass
public class BasicEntity extends Entity {
    private Date modified = new Date();

    @Temporal(TemporalType.TIMESTAMP)
    public Date getModified() {
        return this.modified;
    }

    protected void setModified(Date modified) {
        this.modified = modified;
    }

    @PrePersist
    @PreUpdate
    protected void updateModifiedDate() {
        modified = new Date();
    }
}
