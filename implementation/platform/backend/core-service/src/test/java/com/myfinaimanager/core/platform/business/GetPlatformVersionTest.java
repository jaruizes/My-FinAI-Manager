package com.myfinaimanager.core.platform.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.myfinaimanager.core.platform.domain.exceptions.PlatformVersionUnavailableException;
import com.myfinaimanager.core.platform.domain.model.PlatformVersion;
import com.myfinaimanager.core.platform.domain.ports.PlatformVersionRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GetPlatformVersionTest {

    @Test
    void returnsThePersistedVersionWhenPresent() {
        GetPlatformVersion useCase = new GetPlatformVersion(
                () -> Optional.of(PlatformVersion.of("0.1.0")));

        assertThat(useCase.execute()).isEqualTo(PlatformVersion.of("0.1.0"));
    }

    @Test
    void failsExplicitlyWhenNoVersionIsPersisted() {
        GetPlatformVersion useCase = new GetPlatformVersion(Optional::empty);

        assertThatThrownBy(useCase::execute)
                .isInstanceOf(PlatformVersionUnavailableException.class);
    }

    @Test
    void propagatesPersistenceUnavailability() {
        PlatformVersionRepository failing = () -> {
            throw new PlatformVersionUnavailableException("db down");
        };
        GetPlatformVersion useCase = new GetPlatformVersion(failing);

        assertThatThrownBy(useCase::execute)
                .isInstanceOf(PlatformVersionUnavailableException.class);
    }
}
