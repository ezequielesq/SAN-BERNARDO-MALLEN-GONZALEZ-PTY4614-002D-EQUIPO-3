package com.smartrdp.backend.auditoria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.DefaultRevisionEntity;
import org.hibernate.envers.RevisionEntity;

@Getter
@Setter
@Entity
@Table(name = "revinfo")
@RevisionEntity(SmartRdpRevisionListener.class)
public class SmartRdpRevisionEntity extends DefaultRevisionEntity {

    @Column(name = "usuario_email", length = 150)
    private String usuarioEmail;
}
