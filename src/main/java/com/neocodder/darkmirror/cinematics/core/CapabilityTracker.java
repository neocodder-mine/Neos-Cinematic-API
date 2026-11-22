package com.neocodder.darkmirror.cinematics.core;

import com.neocodder.darkmirror.cinematics.api.ClientCapabilities;
import com.neocodder.darkmirror.cinematics.api.ClientFeature;
import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Server-thread-owned capability cache. It retains player UUIDs, never player objects. */
public final class CapabilityTracker {
    private static final Map<MinecraftServer, Map<UUID, ClientCapabilities>> CAPABILITIES = new WeakHashMap<>();

    private CapabilityTracker() {
    }

    /**
     * Internal: records the verified player's supported feature bits.
     *
     * @param server logical server
     * @param playerId UUID from the server-validated connection context
     * @param featureMask capability mask received from that connection
     */
    public static void set(MinecraftServer server, UUID playerId, int featureMask) {
        EnumSet<ClientFeature> features = EnumSet.noneOf(ClientFeature.class);
        for (ClientFeature feature : ClientFeature.values()) {
            if ((featureMask & feature.bit()) != 0) {
                features.add(feature);
            }
        }
        synchronized (CAPABILITIES) {
            CAPABILITIES.computeIfAbsent(server, ignored -> new java.util.HashMap<>())
                    .put(playerId, new ClientCapabilities(features));
        }
    }

    /**
     * Internal: queries the negotiated capability cache.
     *
     * @param server logical server
     * @param playerId player UUID
     * @param feature requested capability
     * @return whether the player negotiated the capability
     */
    public static boolean supports(MinecraftServer server, UUID playerId, ClientFeature feature) {
        synchronized (CAPABILITIES) {
            return CAPABILITIES.getOrDefault(server, Map.of())
                    .getOrDefault(playerId, ClientCapabilities.NONE)
                    .supports(feature);
        }
    }

    /**
     * Internal: removes a disconnected player from the cache.
     *
     * @param server logical server
     * @param playerId disconnected player UUID
     */
    public static void forget(MinecraftServer server, UUID playerId) {
        synchronized (CAPABILITIES) {
            Map<UUID, ClientCapabilities> capabilities = CAPABILITIES.get(server);
            if (capabilities != null) {
                capabilities.remove(playerId);
            }
        }
    }

    /**
     * Internal: drops all capabilities associated with a stopping server.
     *
     * @param server stopping logical server
     */
    public static void clear(MinecraftServer server) {
        synchronized (CAPABILITIES) {
            CAPABILITIES.remove(server);
        }
    }
}
