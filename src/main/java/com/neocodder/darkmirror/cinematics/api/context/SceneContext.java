package com.neocodder.darkmirror.cinematics.api.context;

import com.neocodder.darkmirror.cinematics.api.participant.SceneParticipant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * A short-lived view of the current server tick. Do not retain it or its resolved players.
 * All methods are called on the logical server thread.
 */
public final class SceneContext {
    private final MinecraftServer server;
    private final ResourceLocation sceneId;
    private final List<SceneParticipant> participants;
    private final SceneActorRegistry actors;

    /**
     * Creates a server-thread context. Addons should receive this from an action,
     * not construct or retain it.
     *
     * @param server current logical server
     * @param sceneId current registered scene ID
     * @param participants immutable participant snapshot
     */
    public SceneContext(MinecraftServer server, ResourceLocation sceneId, List<SceneParticipant> participants) {
        this(server, sceneId, participants, new SceneActorRegistry());
    }

    /**
     * Creates a server-thread context sharing the actor registry of one running scene.
     *
     * @param server current logical server
     * @param sceneId current registered scene ID
     * @param participants immutable participant snapshot
     * @param actors per-scene actor registry
     */
    public SceneContext(MinecraftServer server, ResourceLocation sceneId, List<SceneParticipant> participants,
                        SceneActorRegistry actors) {
        this.server = Objects.requireNonNull(server, "server");
        this.sceneId = Objects.requireNonNull(sceneId, "sceneId");
        this.participants = List.copyOf(participants);
        this.actors = Objects.requireNonNull(actors, "actors");
    }

    /** {@return the current logical server} */
    public MinecraftServer server() {
        return server;
    }

    /** {@return the registered scene definition ID} */
    public ResourceLocation sceneId() {
        return sceneId;
    }

    /** {@return the participant snapshot for this context} */
    public List<SceneParticipant> participants() {
        return participants;
    }

    /** {@return the actor registry shared by actions in this scene instance} */
    public SceneActorRegistry actors() {
        return actors;
    }

    /**
     * Resolves currently connected participants without retaining player references.
     *
     * @return online participant objects for the duration of this call
     */
    public List<ServerPlayer> onlinePlayers() {
        return participants.stream()
                .map(participant -> server.getPlayerList().getPlayer(participant.playerId()))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Resolves an online player by UUID at call time.
     *
     * @param playerId player UUID
     * @return connected player, or {@code null} if offline
     */
    public ServerPlayer player(UUID playerId) {
        return server.getPlayerList().getPlayer(playerId);
    }

    /**
     * Returns a currently loaded server dimension, or {@code null} if it is not loaded.
     *
     * @param dimension dimension key
     * @return loaded server level, or {@code null}
     */
    public Level level(ResourceKey<Level> dimension) {
        return server.getLevel(dimension);
    }

    /**
     * Checks whether every current participant negotiated the requested feature.
     *
     * @param feature requested client capability
     * @return {@code true} when all participants support it
     */
    public boolean allParticipantsSupport(com.neocodder.darkmirror.cinematics.api.ClientFeature feature) {
        return participants.stream().allMatch(participant ->
                com.neocodder.darkmirror.cinematics.core.CapabilityTracker.supports(
                        server, participant.playerId(), feature));
    }
}
