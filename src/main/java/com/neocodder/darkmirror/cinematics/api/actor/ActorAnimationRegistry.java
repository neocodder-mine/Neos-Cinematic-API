package com.neocodder.darkmirror.cinematics.api.actor;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

/** Registry of animation adapters supplied by the API or addon mods. */
public final class ActorAnimationRegistry {
    private final Map<ResourceLocation, ActorAnimationAdapter> adapters = new ConcurrentHashMap<>();

    /**
     * Registers an adapter under an addon-owned ID.
     *
     * @param id namespaced adapter ID
     * @param adapter adapter implementation
     * @throws IllegalStateException if the ID has already been registered
     */
    public void register(ResourceLocation id, ActorAnimationAdapter adapter) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(adapter, "adapter");
        if (adapters.putIfAbsent(id, adapter) != null) {
            throw new IllegalStateException("An actor animation adapter is already registered as " + id);
        }
    }

    /**
     * Finds an adapter by ID.
     *
     * @param id adapter ID
     * @return registered adapter, if any
     */
    public Optional<ActorAnimationAdapter> find(ResourceLocation id) {
        return Optional.ofNullable(adapters.get(Objects.requireNonNull(id, "id")));
    }
}
