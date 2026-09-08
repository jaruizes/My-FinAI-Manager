package com.myfinaimanager.core.platform.infrastructure.persistence.repository;

import com.myfinaimanager.core.platform.infrastructure.persistence.entity.PlatformVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformVersionJpaRepository extends JpaRepository<PlatformVersionEntity, Short> {
}
