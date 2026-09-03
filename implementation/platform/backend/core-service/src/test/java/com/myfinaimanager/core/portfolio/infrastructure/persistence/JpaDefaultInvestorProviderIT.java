package com.myfinaimanager.core.portfolio.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.myfinaimanager.core.portfolio.domain.ports.DefaultInvestorProvider;
import com.myfinaimanager.core.support.PostgresContainerSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** {@link JpaDefaultInvestorProvider} against a real PostgreSQL with the {@code V2} seed. */
@SpringBootTest
class JpaDefaultInvestorProviderIT extends PostgresContainerSupport {

    @Autowired
    private DefaultInvestorProvider provider;

    @Test
    void returns_the_seeded_default_investor() {
        assertThat(provider.get().value())
                .isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    }
}
