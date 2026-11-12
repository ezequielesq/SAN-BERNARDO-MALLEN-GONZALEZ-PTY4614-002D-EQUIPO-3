package com.smartrdp.backend.auditoria;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

@Getter
@Setter
@Entity
@Table(name = "revinfo")
@RevisionEntity(SmartRdpRevisionListener.class)
public class SmartRdpRevisionEntity {

    @Id
    @GeneratedValue
    @RevisionNumber
    @Column(name = "rev")
    private int rev;

    @RevisionTimestamp
    @Column(name = "revtstmp")
    private long timestamp;

    @Column(name = "usuario_email", length = 150)
    private String usuarioEmail;
}
