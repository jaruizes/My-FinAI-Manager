package com.myfinaimanager.core.platform.domain.ports;

import com.myfinaimanager.core.platform.domain.model.PlatformVersion;
import java.util.Optional;

/**
 * Outbound port giving the business core read access to the persisted platform
 * version. Implemented by an infrastructure persistence adapter. Uses only
 * domain types — no JPA entities, SQL, or framework types cross this boundary.
 */
public interface PlatformVersionRepository {

    /**
     * @return the persisted platform version, or {@link Optional#empty()} when no
     *     version record exists.
     * @throws com.myfinaimanager.core.platform.domain.exceptions.PlatformVersionUnavailableException
     *     when persistence cannot be reached to answer the query.
     */
    Optional<PlatformVersion> find();
}
