package com.neocodder.darkmirror.cinematics.api.context;

import com.mojang.logging.LogUtils;
import com.neocodder.darkmirror.cinematics.api.actor.ActorAnimationAdapter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

/**
 * Per-running-scene references to scripted actors.
 *
 * <p>Access this registry only from the server thread while an action is running.</p>
 */
public final class SceneActorRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<String, ActorEntry> actors = new LinkedHashMap<>();
    private final Map<String, ActiveAnimation> activeAnimations = new LinkedHashMap<>();

    /** Creates an empty registry for one running scene. */
    public SceneActorRegistry() {
    }

    /**
     * Resolves an actor by the ID used in the scene definition.
     *
     * @param id scene-local actor ID
     * @return the live entity, if the actor was spawned and has not been removed
     */
    public Optional<Entity> find(String id) {
        ActorEntry entry = actors.get(Objects.requireNonNull(id, "id"));
        return entry == null || entry.entity().isRemoved()
                ? Optional.empty()
                : Optional.of(entry.entity());
    }

    /**
     * Registers an actor for the current scene.
     *
     * @param id unique scene-local ID
     * @param entity server entity
     * @param temporary whether the entity is removed when the scene terminates
     */
    public void register(String id, Entity entity, boolean temporary) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(entity, "entity");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Actor ID cannot be blank");
        }
        if (actors.putIfAbsent(id, new ActorEntry(entity, temporary)) != null) {
            throw new IllegalStateException("An actor is already registered as " + id);
        }
    }

    /**
     * Changes whether an actor is removed automatically when the scene terminates.
     *
     * @param id scene-local actor ID
     * @param temporary true to remove at termination
     */
    public void setTemporary(String id, boolean temporary) {
        ActorEntry entry = require(id);
        actors.put(id, new ActorEntry(entry.entity(), temporary));
    }

    /**
     * Removes an actor from the scene and discards its entity.
     *
     * @param id scene-local actor ID
     * @return whether the actor was registered
     */
    public boolean despawn(String id) {
        Objects.requireNonNull(id, "id");
        ActorEntry entry = actors.get(id);
        if (entry == null) {
            return false;
        }
        try {
            stopAnimation(id);
        } finally {
            actors.remove(id);
            if (!entry.entity().isRemoved()) {
                entry.entity().discard();
            }
        }
        return true;
    }

    /**
     * Starts an adapter-managed animation and stops any animation previously tracked for this actor.
     *
     * @param id scene-local actor ID
     * @param adapterId animation adapter ID
     * @param animationId adapter-specific animation ID
     * @param adapter adapter implementation
     */
    public void startAnimation(String id, ResourceLocation adapterId, ResourceLocation animationId,
                               ActorAnimationAdapter adapter) {
        startTrackedAnimation(id, adapterId, animationId, adapter);
    }

    /**
     * Starts an animation and returns a server-thread cleanup callback for that specific playback.
     *
     * @param id scene-local actor ID
     * @param adapterId animation adapter ID
     * @param animationId adapter-specific animation ID
     * @param adapter adapter implementation
     * @return a callback that only stops this playback if it is still active
     */
    public Runnable startTrackedAnimation(String id, ResourceLocation adapterId, ResourceLocation animationId,
                                          ActorAnimationAdapter adapter) {
        Entity entity = require(id).entity();
        stopAnimation(id);
        ActiveAnimation playback = new ActiveAnimation(adapterId, animationId, adapter);
        activeAnimations.put(id, playback);
        Runnable stopPlayback = () -> {
            if (activeAnimations.get(id) == playback) {
                stopAnimation(id);
            }
        };
        try {
            adapter.play(entity, animationId);
        } catch (RuntimeException exception) {
            try {
                stopPlayback.run();
            } catch (RuntimeException cleanupFailure) {
                LOGGER.error("Could not clean up an animation that failed to start for {}", id, cleanupFailure);
            }
            throw exception;
        }
        return stopPlayback;
    }

    /**
     * Stops the currently tracked adapter-managed animation, if present.
     *
     * @param id scene-local actor ID
     * @return whether a tracked animation was found
     */
    public boolean stopAnimation(String id) {
        ActiveAnimation animation = activeAnimations.remove(Objects.requireNonNull(id, "id"));
        ActorEntry entry = actors.get(id);
        if (animation == null) {
            return false;
        }
        if (entry != null && !entry.entity().isRemoved()) {
            animation.adapter().stop(entry.entity(), animation.animationId());
        }
        return true;
    }

    /**
     * Stops a tracked animation only if it still matches the supplied animation ID.
     *
     * @param id scene-local actor ID
     * @param animationId expected animation ID
     * @return whether the matching animation was stopped
     */
    public boolean stopAnimation(String id, ResourceLocation animationId) {
        ActiveAnimation animation = activeAnimations.get(Objects.requireNonNull(id, "id"));
        if (animation != null && animation.animationId().equals(animationId)) {
            return stopAnimation(id);
        }
        return false;
    }

    /**
     * Stops a tracked animation if its adapter and animation IDs both match.
     *
     * @param id scene-local actor ID
     * @param adapterId expected adapter ID
     * @param animationId expected animation ID
     * @return whether the matching animation was stopped
     */
    public boolean stopAnimation(String id, ResourceLocation adapterId, ResourceLocation animationId) {
        ActiveAnimation animation = activeAnimations.get(Objects.requireNonNull(id, "id"));
        return animation != null && animation.adapterId().equals(adapterId)
                && animation.animationId().equals(animationId) && stopAnimation(id);
    }

    /**
     * Discards temporary actors and releases all scene-local entity references.
     * Intended for the scene manager's terminal cleanup.
     */
    public void cleanup() {
        for (String id : new java.util.ArrayList<>(activeAnimations.keySet())) {
            try {
                stopAnimation(id);
            } catch (RuntimeException exception) {
                LOGGER.error("Could not stop actor animation for {}", id, exception);
            }
        }
        for (ActorEntry entry : actors.values()) {
            if (entry.temporary() && !entry.entity().isRemoved()) {
                entry.entity().discard();
            }
        }
        actors.clear();
    }

    private ActorEntry require(String id) {
        ActorEntry entry = actors.get(Objects.requireNonNull(id, "id"));
        if (entry == null || entry.entity().isRemoved()) {
            throw new IllegalStateException("Actor is not available: " + id);
        }
        return entry;
    }

    private record ActorEntry(Entity entity, boolean temporary) {
    }

    private record ActiveAnimation(ResourceLocation adapterId, ResourceLocation animationId,
                                   ActorAnimationAdapter adapter) {
    }
}
