package com.joselumartos.jwtauthbackenddemo.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

// Indica que esta clase no es una entidad por sí misma (no tendrá una tabla base_entity).
// Sus campos se "mapearán" en las tablas de las clases que la hereden (como Usuario o Producto).
@MappedSuperclass
//  Esta es la más importante. Le dice a JPA que debe "escuchar" eventos del ciclo de vida de la entidad
//  (como antes de insertar o actualizar) para ejecutar la lógica de auditoría de Spring.
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Captura automáticamente la fecha y hora del sistema cuando la entidad se persiste por primera vez (el INSERT).
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Actualiza automáticamente la fecha y hora cada vez que la entidad se modifica (el UPDATE).
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;
}
