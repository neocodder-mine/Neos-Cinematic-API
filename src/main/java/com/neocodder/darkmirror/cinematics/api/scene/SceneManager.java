package com.neocodder.darkmirror.cinematics.api.scene;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;

/** Public operations for starting, inspecting, and stopping server-owned scenes. */
public interface SceneManager {
    /**
     * Starts a registered scene on the server thread.
     *
     * @param server logical server, on its main thread
     * @param sceneId registered scene ID
     * @param participants online participant UUIDs
     * @return the new runtime instance ID
     * @throws IllegalArgumentException if the scene or participants are invalid or a cinematic
     * participant already belongs to another cinematic instance
     * @throws IllegalStateException if called off-thread or the active-scene limit is reached
     */
    UUID start(MinecraftServer server, ResourceLocation sceneId, Collection<UUID> participants);

    /**
     * Adds an online player to an active instance.
     *
     * @param server logical server, on its main thread
     * @param instanceId active scene instance
     * @param playerId connected player UUID
     * @return whether the participant was added; false if another instance already owns
     * the player's cinematic camera
     * @throws IllegalStateException if called off the server thread
     */
    boolean addParticipant(MinecraftServer server, UUID instanceId, UUID playerId);

    /**
     * Removes a player from an instance; removing the last participant cancels it.
     *
     * @param server logical server, on its main thread
     * @param instanceId active scene instance
     * @param playerId participant UUID
     * @return whether the participant was present
     * @throws IllegalStateException if called off the server thread
     */
    boolean removeParticipant(MinecraftServer server, UUID instanceId, UUID playerId);

    /**
     * Cancels an active instance, returning false if it is no longer active.
     *
     * @param server logical server, on its main thread
     * @param instanceId active scene instance
     * @param reason cancellation reason for events and result history
     * @return whether an active instance was cancelled
     * @throws IllegalStateException if called off the server thread
     */
    boolean stop(MinecraftServer server, UUID instanceId, String reason);

    /**
     * Returns immutable snapshots of active instances.
     *
     * @param server logical server, on its main thread
     * @return active scene snapshots
     * @throws IllegalStateException if called off the server thread
     */
    List<SceneInstance> activeScenes(MinecraftServer server);

    /**
     * Finds an immutable snapshot of an active instance.
     *
     * @param server logical server, on its main thread
     * @param instanceId active scene instance
     * @return snapshot when the instance remains active
     * @throws IllegalStateException if called off the server thread
     */
    Optional<SceneInstance> find(MinecraftServer server, UUID instanceId);

    /**
     * Returns a terminal result during its lifecycle event or from the server's bounded history.
     *
     * @param server logical server, on its main thread
     * @param instanceId scene instance
     * @return terminal result if retained
     * @throws IllegalStateException if called off the server thread
     */
    Optional<SceneResult> result(MinecraftServer server, UUID instanceId);
}
