package com.neocodder.darkmirror.cinematics.api.registry;

import com.neocodder.darkmirror.cinematics.api.action.SceneAction;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

/**
 * Registry for addon-provided action factories. Factories receive immutable parameters
 * and must return reusable action definitions with execution state created per scene.
 */
public final class CustomActionRegistry {
    private final Map<ResourceLocation, Function<Map<String, String>, SceneAction>> factories =
            new ConcurrentHashMap<>();

    /** Creates an empty registry for API-owned or addon-owned factories. */
    public CustomActionRegistry() {
    }

    /**
     * Registers an addon factory under a globally unique namespaced ID.
     *
     * @param id stable action factory ID
     * @param factory creates a fresh reusable action definition from parameters
     * @throws IllegalStateException if the ID is already registered
     */
    public void register(ResourceLocation id, Function<Map<String, String>, SceneAction> factory) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(factory, "factory");
        if (factories.putIfAbsent(id, factory) != null) {
            throw new IllegalStateException("A custom action factory is already registered for " + id);
        }
    }

    /**
     * Creates an action using a registered factory and an immutable parameter copy.
     *
     * @param id registered factory ID
     * @param parameters factory parameters
     * @return newly created action definition
     * @throws IllegalArgumentException if no factory exists for the ID
     * @throws NullPointerException if a factory returns {@code null}
     */
    public SceneAction create(ResourceLocation id, Map<String, String> parameters) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(parameters, "parameters");
        Function<Map<String, String>, SceneAction> factory = factories.get(id);
        if (factory == null) {
            throw new IllegalArgumentException("No custom action factory is registered for " + id);
        }
        SceneAction action = factory.apply(Map.copyOf(parameters));
        return Objects.requireNonNull(action, "Custom action factory returned null for " + id);
    }

    /**
     * Returns whether the requested factory ID has been registered.
     *
     * @param id namespaced factory ID
     * @return whether the registry contains it
     */
    public boolean contains(ResourceLocation id) {
        return factories.containsKey(id);
    }
}
