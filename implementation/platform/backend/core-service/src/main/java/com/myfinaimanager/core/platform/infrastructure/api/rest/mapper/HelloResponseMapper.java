package com.myfinaimanager.core.platform.infrastructure.api.rest.mapper;

import com.myfinaimanager.core.platform.domain.model.PlatformVersion;
import com.myfinaimanager.core.platform.infrastructure.api.rest.dto.HelloResponse;

public final class HelloResponseMapper {

    private HelloResponseMapper() {
    }

    public static HelloResponse toResponse(PlatformVersion version) {
        return new HelloResponse(version.value());
    }
}
