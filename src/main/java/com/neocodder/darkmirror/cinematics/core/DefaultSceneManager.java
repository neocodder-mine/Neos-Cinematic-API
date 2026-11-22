package com.neocodder.darkmirror.cinematics.core;

import com.neocodder.darkmirror.cinematics.CinematicsAPI;
import com.neocodder.darkmirror.cinematics.api.ClientFeature;
import com.neocodder.darkmirror.cinematics.api.scene.SceneType;
import com.neocodder.darkmirror.cinematics.network.CinematicsNetwork;
import com.neocodder.darkmirror.cinematics.api.scene.SceneInstance;
import com.neocodder.darkmirror.cinematics.api.scene.SceneManager;
import com.neocodder.darkmirror.cinematics.api.scene.SceneResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/** Internal per-server implementation of the public scene manager facade. */
public final class DefaultSceneManager implements SceneManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_ACTIVE_SCENES_PER_SERVER = 256;
    private final Map<MinecraftServer, LinkedHashMap<UUID, RunningScene>> instances = new WeakHashMap<>();
    private final Map<MinecraftServer, LinkedHashMap<UUID, SceneResult>> results = new WeakHashMap<>();
    private final Map<MinecraftServer, Map<UUID, CinematicPlayerState>> cinematicPlayers = new WeakHashMap<>();
    private final Set<MinecraftServer> stoppingServers = Collections.newSetFromMap(new WeakHashMap<>());

    public DefaultSceneManager() {
    }

    @Override
    public UUID start(MinecraftServer server, net.minecraft.resources.ResourceLocation sceneId,
                      Collection<UUID> participantIds) {
        requireServerThread(server);
        var definition = CinematicsAPI.findScene(sceneId)
                .orElseThrow(() -> new IllegalArgumentException("Scene is not registered: " + sceneId));
        return start(server, definition, participantIds);
    }

    UUID start(MinecraftServer server, com.neocodder.darkmirror.cinematics.api.scene.CinematicScene definition,
               Collection<UUID> participantIds) {
        requireServerThread(server);
        if (stoppingServers.contains(server)) {
            throw new IllegalStateException("The server is stopping");
        }
        List<UUID> uniqueParticipants = participantIds.stream().distinct().toList();
        if (uniqueParticipants.isEmpty()) {
            throw new IllegalArgumentException("A scene must have at least one participant");
        }
        for (UUID playerId : uniqueParticipants) {
            if (server.getPlayerList().getPlayer(playerId) == null) {
                throw new IllegalArgumentException("Participant is not online: " + playerId);
            }
            if (definition.type() == SceneType.CINEMATIC
                    && !CapabilityTracker.supports(server, playerId, ClientFeature.CINEMATIC_CAMERA)) {
                throw new IllegalArgumentException(
                        "Participant does not support cinematic camera mode: " + playerId);
            }
            if (definition.type() == SceneType.CINEMATIC && hasCinematicParticipant(server, playerId)) {
                throw new IllegalArgumentException("Participant already belongs to a cinematic scene: " + playerId);
            }
        }

        LinkedHashMap<UUID, RunningScene> serverInstances =
                instances.computeIfAbsent(server, ignored -> new LinkedHashMap<>());
        if (serverInstances.size() >= MAX_ACTIVE_SCENES_PER_SERVER) {
            throw new IllegalStateException("The server has reached the active scene limit of "
                    + MAX_ACTIVE_SCENES_PER_SERVER);
        }
        RunningScene scene = new RunningScene(definition, uniqueParticipants);
        serverInstances.put(scene.instanceId(), scene);
        if (scene.cinematic()) {
            refreshCinematicPlayers(server);
        }
        scene.start(server);
        removeTerminal(server, scene);
        return scene.instanceId();
    }

    @Override
    public boolean addParticipant(MinecraftServer server, UUID instanceId, UUID playerId) {
        requireServerThread(server);
        RunningScene scene = findRunning(server, instanceId);
        if (scene == null || (scene.cinematic() && hasCinematicParticipant(server, playerId))
                || !scene.addParticipant(server, playerId)) {
            return false;
        }
        if (scene.cinematic()) {
            refreshCinematicPlayers(server);
        }
        return true;
    }

    @Override
    public boolean removeParticipant(MinecraftServer server, UUID instanceId, UUID playerId) {
        requireServerThread(server);
        RunningScene scene = findRunning(server, instanceId);
        if (scene == null || scene.terminal()) {
            return false;
        }
        boolean removed = scene.removeParticipant(server, playerId);
        if (scene.terminal()) {
            removeTerminal(server, scene);
        } else if (removed && scene.cinematic()) {
            refreshCinematicPlayers(server);
        }
        return removed;
    }

    @Override
    public boolean stop(MinecraftServer server, UUID instanceId, String reason) {
        requireServerThread(server);
        RunningScene scene = findRunning(server, instanceId);
        if (scene == null || scene.terminal()) {
            return false;
        }
        scene.stop(server, reason);
        removeTerminal(server, scene);
        return true;
    }

    @Override
    public List<SceneInstance> activeScenes(MinecraftServer server) {
        requireServerThread(server);
        LinkedHashMap<UUID, RunningScene> serverInstances = instances.get(server);
        if (serverInstances == null) {
            return List.of();
        }
        return serverInstances.values().stream()
                .filter(scene -> !scene.terminal())
                .map(RunningScene::snapshot)
                .toList();
    }

    @Override
    public Optional<SceneInstance> find(MinecraftServer server, UUID instanceId) {
        requireServerThread(server);
        RunningScene scene = findRunning(server, instanceId);
        return scene == null || scene.terminal() ? Optional.empty() : Optional.of(scene.snapshot());
    }

    @Override
    public Optional<SceneResult> result(MinecraftServer server, UUID instanceId) {
        requireServerThread(server);
        Map<UUID, SceneResult> serverResults = results.get(server);
        SceneResult stored = serverResults == null ? null : serverResults.get(instanceId);
        if (stored != null) {
            return Optional.of(stored);
        }
        RunningScene scene = findRunning(server, instanceId);
        return scene != null && scene.terminal() ? Optional.of(scene.result()) : Optional.empty();
    }

    /**
     * Advances all active instances after the server tick.
     *
     * @param event completed server tick event
     */
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        LinkedHashMap<UUID, RunningScene> serverInstances = instances.get(server);
        if (serverInstances == null || serverInstances.isEmpty()) {
            return;
        }
        boolean participantsChanged = false;
        for (RunningScene scene : new ArrayList<>(serverInstances.values())) {
            int before = scene.participantCount();
            scene.tick(server);
            removeTerminal(server, scene);
            participantsChanged |= scene.cinematic() && before != scene.participantCount();
        }
        if (participantsChanged) {
            refreshCinematicPlayers(server);
        }
    }

    /** Keeps cinematic participants immobile without changing their server position. */
    public void onPlayerTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return;
        }
        MinecraftServer server = serverPlayer.getServer();
        if (server == null) {
            return;
        }
        CinematicPlayerState state = cinematicPlayers.getOrDefault(server, Map.of()).get(player.getUUID());
        if (state == null) {
            return;
        }
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.setYRot(state.yaw());
        player.setXRot(state.pitch());
    }

    /**
     * Removes logged-out players and their capability cache entries.
     *
     * @param event player logout event
     */
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        Map<UUID, CinematicPlayerState> lockedPlayers = cinematicPlayers.get(server);
        CinematicPlayerState lockedState = lockedPlayers == null ? null : lockedPlayers.remove(player.getUUID());
        if (lockedState != null) {
            lockedState.restore(player);
        }
        LinkedHashMap<UUID, RunningScene> serverInstances = instances.get(server);
        if (serverInstances != null) {
            for (RunningScene scene : new ArrayList<>(serverInstances.values())) {
                scene.removeParticipant(server, player.getUUID());
                removeTerminal(server, scene);
            }
        }
        refreshCinematicPlayers(server);
        CapabilityTracker.forget(server, player.getUUID());
    }

    /**
     * Cancels active instances and clears transient data during shutdown.
     *
     * @param event server stopping event
     */
    public void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        stoppingServers.add(server);
        var serverInstances = instances.get(server);
        if (serverInstances != null) {
            for (RunningScene scene : new ArrayList<>(serverInstances.values())) {
                scene.stop(server, "Server stopping");
                removeTerminal(server, scene);
            }
        }
        restoreCinematicPlayers(server);
        CapabilityTracker.clear(server);
        results.remove(server);
    }

    private RunningScene findRunning(MinecraftServer server, UUID instanceId) {
        Map<UUID, RunningScene> serverInstances = instances.get(server);
        return serverInstances == null ? null : serverInstances.get(instanceId);
    }

    private boolean hasCinematicParticipant(MinecraftServer server, UUID playerId) {
        var serverInstances = instances.get(server);
        return serverInstances != null && serverInstances.values().stream()
                .anyMatch(scene -> scene.cinematic() && scene.hasParticipant(playerId));
    }

    private void removeTerminal(MinecraftServer server, RunningScene scene) {
        if (!scene.terminal()) {
            return;
        }
        Map<UUID, RunningScene> serverInstances = instances.get(server);
        if (serverInstances == null || serverInstances.remove(scene.instanceId()) == null) {
            return;
        }
        results.computeIfAbsent(server, ignored -> new LinkedHashMap<>())
                .put(scene.instanceId(), scene.result());
        trimResults(server);
        if (serverInstances.isEmpty()) {
            instances.remove(server);
        }
        if (scene.cinematic()) {
            refreshCinematicPlayers(server);
        }
        LOGGER.debug("Removed terminal cinematic instance {}", scene.instanceId());
    }

    private void refreshCinematicPlayers(MinecraftServer server) {
        Map<UUID, CinematicPlayerState> current = cinematicPlayers.computeIfAbsent(server,
                ignored -> new LinkedHashMap<>());
        Set<UUID> desired = new LinkedHashSet<>();
        var serverInstances = instances.get(server);
        if (serverInstances != null) {
            for (RunningScene scene : serverInstances.values()) {
                if (scene.cinematic() && !scene.terminal()) {
                    desired.addAll(scene.participantIds());
                }
            }
        }
        for (UUID playerId : new ArrayList<>(current.keySet())) {
            if (desired.contains(playerId)) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            CinematicPlayerState state = current.remove(playerId);
            if (player != null) {
                state.restore(player);
                CinematicsNetwork.sendCameraMode(player, false);
            }
        }
        for (UUID playerId : desired) {
            if (current.containsKey(playerId)) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            current.put(playerId, new CinematicPlayerState(
                    player.isNoGravity(), player.getYRot(), player.getXRot()));
            player.setNoGravity(true);
            player.setDeltaMovement(Vec3.ZERO);
            CinematicsNetwork.sendCameraMode(player, true);
        }
        if (current.isEmpty()) {
            cinematicPlayers.remove(server);
        }
    }

    private void restoreCinematicPlayers(MinecraftServer server) {
        Map<UUID, CinematicPlayerState> current = cinematicPlayers.remove(server);
        if (current == null) {
            return;
        }
        for (Map.Entry<UUID, CinematicPlayerState> entry : current.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                entry.getValue().restore(player);
                CinematicsNetwork.sendCameraMode(player, false);
            }
        }
    }

    private record CinematicPlayerState(boolean noGravity, float yaw, float pitch) {
        void restore(ServerPlayer player) {
            player.setNoGravity(noGravity);
            player.setDeltaMovement(Vec3.ZERO);
            player.setYRot(yaw);
            player.setXRot(pitch);
        }
    }

    private void trimResults(MinecraftServer server) {
        LinkedHashMap<UUID, SceneResult> serverResults = results.get(server);
        while (serverResults != null && serverResults.size() > MAX_ACTIVE_SCENES_PER_SERVER) {
            UUID oldest = serverResults.keySet().iterator().next();
            serverResults.remove(oldest);
        }
    }

    private static void requireServerThread(MinecraftServer server) {
        if (server == null || !server.isSameThread()) {
            throw new IllegalStateException("Cinematic scene operations must run on the server thread");
        }
    }
}
