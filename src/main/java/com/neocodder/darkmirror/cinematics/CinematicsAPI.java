package com.neocodder.darkmirror.cinematics;

import com.neocodder.darkmirror.cinematics.api.ClientFeature;
import com.neocodder.darkmirror.cinematics.api.actor.ActorAnimationRegistry;
import com.neocodder.darkmirror.cinematics.api.action.SceneAction;
import com.neocodder.darkmirror.cinematics.api.registry.CustomActionRegistry;
import com.neocodder.darkmirror.cinematics.api.scene.CinematicScene;
import com.neocodder.darkmirror.cinematics.api.scene.SceneManager;
import com.neocodder.darkmirror.cinematics.core.CapabilityTracker;
import com.neocodder.darkmirror.cinematics.core.CutsceneCommands;
import com.neocodder.darkmirror.cinematics.core.DefaultSceneManager;
import com.neocodder.darkmirror.cinematics.network.CinematicsNetwork;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Stable addon entry point. Registry data is shared; runtime instances remain
 * server-owned.
 */
public final class CinematicsAPI {
    private static final Map<ResourceLocation, CinematicScene> SCENES = new ConcurrentHashMap<>();
    private static final CustomActionRegistry CUSTOM_ACTIONS = new CustomActionRegistry();
    private static final ActorAnimationRegistry ACTOR_ANIMATIONS = new ActorAnimationRegistry();
    private static final DefaultSceneManager SCENE_MANAGER = new DefaultSceneManager();
    private static boolean initialized;

    private CinematicsAPI() {
    }

    /**
     * Registers API lifecycle and networking handlers. Called only by the mod entry point.
     *
     * @param modEventBus event bus provided to the API mod constructor
     */
    public static synchronized void initialize(IEventBus modEventBus) {
        if (initialized) {
            return;
        }
        initialized = true;
        modEventBus.addListener(CinematicsNetwork::registerPayloads);
        NeoForge.EVENT_BUS.addListener(SCENE_MANAGER::onServerTick);
        NeoForge.EVENT_BUS.addListener(SCENE_MANAGER::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(SCENE_MANAGER::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(SCENE_MANAGER::onServerStopping);
        new CutsceneCommands(SCENE_MANAGER, NeoForge.EVENT_BUS);
    }

    /**
     * Registers a reusable scene definition.
     *
     * @param scene immutable scene definition
     * @throws IllegalStateException if the ID is already registered
     */
    public static void registerScene(CinematicScene scene) {
        if (SCENES.putIfAbsent(scene.id(), scene) != null) {
            throw new IllegalStateException("A cinematic scene is already registered as " + scene.id());
        }
    }

    /**
     * Finds a registered definition without creating a runtime instance.
     *
     * @param id namespaced scene ID
     * @return the definition if registered
     */
    public static Optional<CinematicScene> findScene(ResourceLocation id) {
        return Optional.ofNullable(SCENES.get(id));
    }

    /** {@return an immutable snapshot of registered scene IDs} */
    public static Set<ResourceLocation> registeredSceneIds() {
        return Set.copyOf(SCENES.keySet());
    }

    /** {@return the server-owned scene manager} */
    public static SceneManager scenes() {
        return SCENE_MANAGER;
    }

    /** {@return the registry for addon action factories} */
    public static CustomActionRegistry customActions() {
        return CUSTOM_ACTIONS;
    }

    /** {@return the registry for addon animation adapters} */
    public static ActorAnimationRegistry actorAnimations() {
        return ACTOR_ANIMATIONS;
    }

    /**
     * Checks the feature mask negotiated for a player on this server.
     *
     * @param server logical server
     * @param playerId player UUID
     * @param feature requested feature
     * @return whether the player negotiated the feature
     */
    public static boolean supports(MinecraftServer server, java.util.UUID playerId, ClientFeature feature) {
        return CapabilityTracker.supports(server, playerId, feature);
    }
}
