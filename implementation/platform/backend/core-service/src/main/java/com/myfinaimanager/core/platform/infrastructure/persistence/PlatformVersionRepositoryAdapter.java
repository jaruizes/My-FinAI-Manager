package com.myfinaimanager.core.platform.infrastructure.persistence;

import com.myfinaimanager.core.platform.domain.exceptions.PlatformVersionUnavailableException;
import com.myfinaimanager.core.platform.domain.model.PlatformVersion;
import com.myfinaimanager.core.platform.domain.ports.PlatformVersionRepository;
import com.myfinaimanager.core.platform.infrastructure.persistence.entity.PlatformVersionEntity;
import com.myfinaimanager.core.platform.infrastructure.persistence.repository.PlatformVersionJpaRepository;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * Persistence adapter implementing the domain {@link PlatformVersionRepository}
 * port. Maps the JPA entity to the {@link PlatformVersion} domain value object
 * and translates infrastructure failures into the neutral domain exception
 * (DR-007, AC-008) — no SQL or framework detail crosses the port.
 */
@Component
public class PlatformVersionRepositoryAdapter implements PlatformVersionRepository {

    private final PlatformVersionJpaRepository jpaRepository;

    public PlatformVersionRepositoryAdapter(PlatformVersionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<PlatformVersion> find() {
        try {
            return jpaRepository.findAll().stream()
                    .findFirst()
                    .map(PlatformVersionEntity::getVersion)
                    .map(PlatformVersion::of);
        } catch (DataAccessException ex) {
            throw new PlatformVersionUnavailableException(
                    "Platform version could not be read from persistence", ex);
        }
    }
}
