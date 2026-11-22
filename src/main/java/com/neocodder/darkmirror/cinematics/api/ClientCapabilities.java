package com.neocodder.darkmirror.cinematics.api;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Immutable set of optional presentation features supported by a connected client.
 *
 * @param features supported optional features
 */
public record ClientCapabilities(Set<ClientFeature> features) {
    /** Capability set used before the connection handshake is received. */
    public static final ClientCapabilities NONE = new ClientCapabilities(Set.of());

    /** Canonical constructor that stores an immutable copy of the input set. */
    public ClientCapabilities {
        features = features.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(features));
    }

    /**
     * Returns whether the client supports the requested presentation feature.
     *
     * @param feature capability to check
     * @return {@code true} if supported
     */
    public boolean supports(ClientFeature feature) {
        return features.contains(feature);
    }
}
