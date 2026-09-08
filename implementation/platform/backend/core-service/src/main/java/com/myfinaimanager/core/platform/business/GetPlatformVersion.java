package com.myfinaimanager.core.platform.business;

import com.myfinaimanager.core.platform.domain.exceptions.PlatformVersionUnavailableException;
import com.myfinaimanager.core.platform.domain.model.PlatformVersion;
import com.myfinaimanager.core.platform.domain.ports.PlatformVersionRepository;
import org.springframework.stereotype.Service;

/**
 * Use case behind {@code GET /api/v1/hello}: return the platform version read
 * from persistence, or fail explicitly when it is unavailable (EN001 FR-004,
 * BR-001, AC-008).
 */
@Service
public class GetPlatformVersion {

    private final PlatformVersionRepository repository;

    public GetPlatformVersion(PlatformVersionRepository repository) {
        this.repository = repository;
    }

    public PlatformVersion execute() {
        return repository.find()
                .orElseThrow(() -> new PlatformVersionUnavailableException(
                        "No platform version is available from persistence"));
    }
}
