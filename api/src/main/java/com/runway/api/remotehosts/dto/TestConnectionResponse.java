package com.runway.api.remotehosts.dto;

public record TestConnectionResponse(
        boolean success,
        String hostKeyFingerprint,
        String hostKeyAlgorithm,
        boolean newlyPinned,
        String errorMessage) {
}
