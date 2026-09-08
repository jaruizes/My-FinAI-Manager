package com.myfinaimanager.core.platform.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA mapping for the single-row {@code platform_version} table. Infrastructure
 * model — never used as a domain type (AAC-012).
 */
@Entity
@Table(name = "platform_version")
public class PlatformVersionEntity {

    @Id
    @Column(name = "id")
    private Short id;

    @Column(name = "version", nullable = false)
    private String version;

    protected PlatformVersionEntity() {
        // required by JPA
    }

    public Short getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }
}
