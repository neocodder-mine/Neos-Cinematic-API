package com.neocodder.darkmirror.cinematics.api.action;

import com.mojang.logging.LogUtils;
import com.neocodder.darkmirror.cinematics.CinematicsAPI;
import com.neocodder.darkmirror.cinematics.api.ClientFeature;
import com.neocodder.darkmirror.cinematics.api.context.SceneContext;
import com.neocodder.darkmirror.cinematics.network.CinematicsNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

/**
 * Factory for the API's built-in, immutable action definitions. Every factory
 * validates its arguments before a scene is started.
 */
public final class BuiltInActions {
    private static final Logger LOGGER = LogUtils.getLogger();

    private BuiltInActions() {
    }

    /**
     * Creates a non-blocking wait action.
     *
     * @param ticks delay length; zero completes on the first action tick
     * @return reusable wait action
     */
    public static SceneAction waitTicks(int ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks must be zero or greater");
        }
        return action(() -> new ActionExecution() {
            private int remaining = ticks;

            @Override
            public ActionResult tick(SceneContext context) {
                if (remaining == 0) {
                    return ActionResult.completed();
                }
                remaining--;
                return remaining == 0 ? ActionResult.completed() : ActionResult.running();
            }
        });
    }

    /**
     * Creates concurrent executions that complete when every child completes.
     *
     * @param actions non-empty child action definitions
     * @return parallel action group
     */
    public static SceneAction parallel(List<SceneAction> actions) {
        List<SceneAction> definitions = List.copyOf(actions);
        if (definitions.isEmpty()) {
            throw new IllegalArgumentException("A parallel group requires at least one action");
        }
        Set<ClientFeature> requiredFeatures = definitions.stream()
                .filter(definition -> !definition.optional())
                .flatMap(definition -> definition.requiredFeatures().stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new SceneAction() {
            @Override
            public ActionExecution createExecution() {
                return new ActionExecution() {
                    private final ActionExecution[] executions = new ActionExecution[definitions.size()];
                    private final boolean[] complete = new boolean[executions.length];
                    private boolean cancelled;

                    @Override
                    public ActionResult tick(SceneContext context) {
                        boolean allComplete = true;
                        for (int i = 0; i < executions.length && !cancelled; i++) {
                            if (complete[i]) {
                                continue;
                            }
                            SceneAction definition = definitions.get(i);
                            ActionResult result;
                            try {
                                if (definition.requiredFeatures().stream()
                                        .anyMatch(feature -> !context.allParticipantsSupport(feature))) {
                                    result = ActionResult.failed("A parallel action requires an unsupported client feature");
                                } else {
                                    if (executions[i] == null) {
                                        executions[i] = Objects.requireNonNull(definition.createExecution(),
                                                "A parallel action returned no execution");
                                    }
                                    if (cancelled) {
                                        cancelChild(i, context);
                                        break;
                                    }
                                    result = Objects.requireNonNull(executions[i].tick(context),
                                            "A parallel action returned no result");
                                }
                            } catch (RuntimeException exception) {
                                if (!definition.optional()) {
                                    throw exception;
                                }
                                LOGGER.warn("Optional parallel action {} threw an exception", i, exception);
                                cancelChild(i, context);
                                continue;
                            }
                            if (cancelled) {
                                break;
                            }
                            if (result.status() == ActionResult.Status.FAILED) {
                                if (definition.optional()) {
                                    LOGGER.warn("Skipping optional parallel action {}: {}", i, result.message());
                                    cancelChild(i, context);
                                    continue;
                                }
                                return result;
                            }
                            if (result.status() == ActionResult.Status.FINISH_SCENE) {
                                complete[i] = true;
                                return result;
                            }
                            complete[i] = result.status() == ActionResult.Status.COMPLETED;
                            allComplete &= complete[i];
                        }
                        return allComplete ? ActionResult.completed() : ActionResult.running();
                    }

                    @Override
                    public void cancel(SceneContext context) {
                        cancelled = true;
                        for (int i = 0; i < executions.length; i++) {
                            if (!complete[i]) {
                                cancelChild(i, context);
                            }
                        }
                    }

                    private void cancelChild(int index, SceneContext context) {
                        ActionExecution child = executions[index];
                        complete[index] = true;
                        executions[index] = null;
                        if (child != null) {
                            try {
                                child.cancel(context);
                            } catch (RuntimeException exception) {
                                LOGGER.error("Parallel action {} failed during cleanup", index, exception);
                            }
                        }
                    }
                };
            }

            @Override
            public Set<ClientFeature> requiredFeatures() {
                return requiredFeatures;
            }
        };
    }

    /**
     * Plays a registered sound separately to each online participant.
     *
     * @param soundId registered sound event ID
     * @param volume non-negative sound volume
     * @param pitch positive sound pitch
     * @return sound action
     */
    public static SceneAction playSound(ResourceLocation soundId, float volume, float pitch) {
        Objects.requireNonNull(soundId, "soundId");
        if (!Float.isFinite(volume) || volume < 0 || !Float.isFinite(pitch) || pitch <= 0) {
            throw new IllegalArgumentException("Sound volume and pitch must be finite and valid");
        }
        return action(() -> context -> {
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(soundId).orElse(null);
            if (sound == null) {
                return ActionResult.failed("Unknown sound event: " + soundId);
            }
            for (ServerPlayer player : context.onlinePlayers()) {
                player.playNotifySound(sound, SoundSource.MASTER, volume, pitch);
            }
            return ActionResult.completed();
        });
    }

    /**
     * Sends vanilla title and subtitle packets to online participants.
     *
     * @param title title component
     * @param subtitle subtitle component
     * @param fadeInTicks fade-in duration
     * @param stayTicks visible duration
     * @param fadeOutTicks fade-out duration
     * @return title action
     */
    public static SceneAction title(Component title, Component subtitle, int fadeInTicks, int stayTicks,
                                    int fadeOutTicks) {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(subtitle, "subtitle");
        if (fadeInTicks < 0 || stayTicks < 0 || fadeOutTicks < 0) {
            throw new IllegalArgumentException("Title timings cannot be negative");
        }
        return action(() -> context -> {
            for (ServerPlayer player : context.onlinePlayers()) {
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(
                        fadeInTicks, stayTicks, fadeOutTicks));
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(title));
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(subtitle));
            }
            return ActionResult.completed();
        });
    }

    /**
     * Displays timed dialogue, using a plain system message without the client API.
     *
     * @param line dialogue line
     * @param durationTicks display duration
     * @return dialogue action
     */
    public static SceneAction dialogue(Component line, int durationTicks) {
        Objects.requireNonNull(line, "line");
        if (durationTicks < 1) {
            throw new IllegalArgumentException("durationTicks must be positive");
        }
        if (durationTicks > 20 * 60 * 10 || line.getString().length() > 2048) {
            throw new IllegalArgumentException("Dialogue text or duration exceeds the supported range");
        }
        return () -> context -> {
            String text = line.getString();
            for (ServerPlayer player : context.onlinePlayers()) {
                if (CinematicsAPI.supports(context.server(), player.getUUID(), ClientFeature.DIALOGUE_OVERLAY)) {
                    CinematicsNetwork.sendDialogue(player, text, durationTicks);
                } else {
                    player.displayClientMessage(line, false);
                }
            }
            return ActionResult.completed();
        };
    }

    /**
     * Fades the client overlay from transparent to opaque for API clients.
     *
     * @param durationTicks fade duration
     * @return optional presentation action
     */
    public static SceneAction fadeOut(int durationTicks) {
        return fade(durationTicks, true);
    }

    /**
     * Fades the client overlay from opaque to transparent for API clients.
     *
     * @param durationTicks fade duration
     * @return optional presentation action
     */
    public static SceneAction fadeIn(int durationTicks) {
        return fade(durationTicks, false);
    }

    /**
     * Moves the independent cinematic camera smoothly to a world position and rotation.
     *
     * @param x target X coordinate
     * @param y target Y coordinate
     * @param z target Z coordinate
     * @param yaw target yaw
     * @param pitch target pitch
     * @param durationTicks movement duration in ticks
     * @return camera movement action
     */
    public static SceneAction cameraMove(double x, double y, double z, float yaw, float pitch, int durationTicks) {
        requireFinitePosition(x, y, z);
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("Camera rotation must be finite");
        }
        requireCameraDuration(durationTicks);
        return cameraAction(() -> new ActionExecution() {
            private int remaining = durationTicks;
            private boolean sent;

            @Override
            public ActionResult tick(SceneContext context) {
                if (!sent) {
                    for (ServerPlayer player : context.onlinePlayers()) {
                        CinematicsNetwork.sendCameraMove(player, x, y, z, yaw, pitch, durationTicks);
                    }
                    sent = true;
                }
                remaining--;
                return remaining <= 0 ? ActionResult.completed() : ActionResult.running();
            }
        });
    }

    /**
     * Follows an entity tracked by every participant's client in their current dimension.
     *
     * @param entityId runtime entity ID
     * @param offsetX camera X offset
     * @param offsetY camera Y offset
     * @param offsetZ camera Z offset
     * @param durationTicks follow duration in ticks
     * @return entity-follow camera action
     */
    public static SceneAction cameraFollowEntity(int entityId, double offsetX, double offsetY, double offsetZ,
                                                 int durationTicks) {
        if (entityId < 0) {
            throw new IllegalArgumentException("Camera entity ID cannot be negative");
        }
        return cameraFollowEntity(context -> {
            for (ServerLevel level : context.server().getAllLevels()) {
                Entity target = level.getEntity(entityId);
                if (target != null) {
                    return target;
                }
            }
            return null;
        }, offsetX, offsetY, offsetZ, durationTicks);
    }

    /**
     * Follows an entity resolved when the action begins, allowing runtime-created NPCs.
     *
     * @param targetResolver server-thread resolver for the current target entity
     * @param offsetX camera X offset
     * @param offsetY camera Y offset
     * @param offsetZ camera Z offset
     * @param durationTicks follow duration in ticks
     * @return entity-follow camera action
     */
    public static SceneAction cameraFollowEntity(Function<SceneContext, ? extends Entity> targetResolver,
                                                 double offsetX, double offsetY, double offsetZ,
                                                 int durationTicks) {
        Objects.requireNonNull(targetResolver, "targetResolver");
        if (!Double.isFinite(offsetX) || !Double.isFinite(offsetY) || !Double.isFinite(offsetZ)) {
            throw new IllegalArgumentException("Camera offsets must be finite");
        }
        requireCameraDuration(durationTicks);
        return cameraAction(() -> new ActionExecution() {
            private int remaining = durationTicks;
            private boolean sent;

            @Override
            public ActionResult tick(SceneContext context) {
                if (!sent) {
                    Entity target = targetResolver.apply(context);
                    if (target == null) {
                        return ActionResult.failed("Camera target resolver returned no entity");
                    }
                    List<ServerPlayer> players = context.onlinePlayers();
                    for (ServerPlayer player : players) {
                        if (player.level() != target.level()) {
                            return ActionResult.failed(
                                    "Camera target entity is not in every participant's dimension: "
                                            + target.getUUID());
                        }
                    }
                    for (ServerPlayer player : players) {
                        CinematicsNetwork.sendCameraFollow(
                                player, target.getId(), offsetX, offsetY, offsetZ, durationTicks);
                    }
                    sent = true;
                }
                remaining--;
                return remaining <= 0 ? ActionResult.completed() : ActionResult.running();
            }
        });
    }

    private static SceneAction cameraAction(java.util.function.Supplier<ActionExecution> factory) {
        return new SceneAction() {
            @Override
            public ActionExecution createExecution() {
                return factory.get();
            }

            @Override
            public Set<ClientFeature> requiredFeatures() {
                return Set.of(ClientFeature.CINEMATIC_CAMERA);
            }
        };
    }

    private static void requireCameraDuration(int durationTicks) {
        if (durationTicks < 1 || durationTicks > 20 * 60 * 10) {
            throw new IllegalArgumentException("durationTicks must be between 1 and 12000");
        }
    }

    private static SceneAction fade(int durationTicks, boolean fadeOut) {
        if (durationTicks < 1 || durationTicks > 20 * 60 * 10) {
            throw new IllegalArgumentException("durationTicks must be between 1 and 12000");
        }
        return new SceneAction() {
            @Override
            public ActionExecution createExecution() {
                return new ActionExecution() {
                    private int remaining = durationTicks;
                    private boolean sent;

                    @Override
                    public ActionResult tick(SceneContext context) {
                        if (!sent) {
                            for (ServerPlayer player : context.onlinePlayers()) {
                                if (com.neocodder.darkmirror.cinematics.CinematicsAPI.supports(
                                        context.server(), player.getUUID(), ClientFeature.SCREEN_FADE)) {
                                    com.neocodder.darkmirror.cinematics.network.CinematicsNetwork.sendFade(
                                            player, fadeOut, durationTicks);
                                }
                            }
                            sent = true;
                        }
                        remaining--;
                        return remaining <= 0 ? ActionResult.completed() : ActionResult.running();
                    }
                };
            }

            @Override
            public boolean optional() {
                return true;
            }
        };
    }

    /**
     * Creates an entity on the server in a loaded dimension.
     *
     * @param entityTypeId registered entity type ID
     * @param dimension loaded dimension ID
     * @param x target X coordinate
     * @param y target Y coordinate
     * @param z target Z coordinate
     * @return server-side spawn action
     */
    public static SceneAction spawnEntity(ResourceLocation entityTypeId, ResourceLocation dimension,
                                         double x, double y, double z) {
        Objects.requireNonNull(entityTypeId, "entityTypeId");
        Objects.requireNonNull(dimension, "dimension");
        requireFinitePosition(x, y, z);
        return action(() -> context -> {
            ResourceKey<Level> dimensionKey = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    dimension);
            Level level = context.level(dimensionKey);
            if (!(level instanceof ServerLevel serverLevel)) {
                return ActionResult.failed("Target dimension is not loaded: " + dimension);
            }
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entityTypeId).orElse(null);
            if (type == null) {
                return ActionResult.failed("Unknown entity type: " + entityTypeId);
            }
            Entity entity = type.create(serverLevel);
            if (entity == null) {
                return ActionResult.failed("Entity type could not create an entity: " + entityTypeId);
            }
            entity.moveTo(x, y, z, entity.getYRot(), entity.getXRot());
            return serverLevel.addFreshEntity(entity)
                    ? ActionResult.completed()
                    : ActionResult.failed("The world rejected entity spawn: " + entityTypeId);
        });
    }

    /**
     * Removes a UUID-addressed entity from the specified loaded dimension.
     *
     * @param dimension loaded dimension ID
     * @param entityId entity UUID
     * @return removal action
     */
    public static SceneAction removeEntity(ResourceLocation dimension, UUID entityId) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(entityId, "entityId");
        return action(() -> context -> {
            ResourceKey<Level> dimensionKey = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    dimension);
            Level level = context.level(dimensionKey);
            if (!(level instanceof ServerLevel serverLevel)) {
                return ActionResult.failed("Target dimension is not loaded: " + dimension);
            }
            Entity entity = serverLevel.getEntity(entityId);
            if (entity == null) {
                return ActionResult.failed("Entity is not present in the target dimension: " + entityId);
            }
            entity.discard();
            return ActionResult.completed();
        });
    }

    /**
     * Teleports connected participants to an already loaded dimension.
     *
     * @param dimension loaded destination dimension
     * @param x destination X coordinate
     * @param y destination Y coordinate
     * @param z destination Z coordinate
     * @param yaw destination yaw
     * @param pitch destination pitch
     * @return teleport action
     */
    public static SceneAction teleportParticipants(ResourceLocation dimension, double x, double y, double z,
                                                  float yaw, float pitch) {
        Objects.requireNonNull(dimension, "dimension");
        requireFinitePosition(x, y, z);
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("Rotation must be finite");
        }
        return action(() -> context -> {
            ResourceKey<Level> dimensionKey = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    dimension);
            Level level = context.level(dimensionKey);
            if (!(level instanceof ServerLevel target)) {
                return ActionResult.failed("Target dimension is not loaded: " + dimension);
            }
            List<UUID> missing = new ArrayList<>();
            for (var participant : context.participants()) {
                ServerPlayer player = context.player(participant.playerId());
                if (player == null) {
                    missing.add(participant.playerId());
                } else {
                    player.teleportTo(target, x, y, z, yaw, pitch);
                }
            }
            return missing.isEmpty()
                    ? ActionResult.completed()
                    : ActionResult.failed("Disconnected participants were not teleported: " + missing);
        });
    }

    /**
     * Sends a registered simple particle type from the server.
     *
     * @param particleId registered simple particle ID
     * @param dimension loaded dimension ID
     * @param x particle origin X
     * @param y particle origin Y
     * @param z particle origin Z
     * @param count particle count, between zero and 10000
     * @param speed particle spread speed
     * @return server particle action
     */
    public static SceneAction spawnParticles(ResourceLocation particleId, ResourceLocation dimension,
                                             double x, double y, double z, int count, double speed) {
        Objects.requireNonNull(particleId, "particleId");
        Objects.requireNonNull(dimension, "dimension");
        requireFinitePosition(x, y, z);
        if (count < 0 || count > 10000 || !Double.isFinite(speed) || speed < 0) {
            throw new IllegalArgumentException("Particle count or speed is outside the supported range");
        }
        return action(() -> context -> {
            ResourceKey<Level> key = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    dimension);
            Level level = context.level(key);
            if (!(level instanceof ServerLevel serverLevel)) {
                return ActionResult.failed("Target dimension is not loaded: " + dimension);
            }
            var particle = BuiltInRegistries.PARTICLE_TYPE.getOptional(particleId).orElse(null);
            if (!(particle instanceof SimpleParticleType simpleParticle)) {
                return ActionResult.failed("Unknown or data-driven particle type: " + particleId);
            }
            serverLevel.sendParticles(simpleParticle, x, y, z, count, 0.0, 0.0, 0.0, speed);
            return ActionResult.completed();
        });
    }

    /**
     * Runs a short callback on the server thread.
     *
     * @param callback quick, non-blocking server callback
     * @return callback action
     */
    public static SceneAction serverAction(Consumer<SceneContext> callback) {
        Objects.requireNonNull(callback, "callback");
        return action(() -> context -> {
            callback.accept(context);
            return ActionResult.completed();
        });
    }

    /**
     * Creates an action that completes its containing scene immediately.
     *
     * @return scene-finish action
     */
    public static SceneAction finish() {
        return action(() -> context -> ActionResult.finishScene());
    }

    private static SceneAction action(java.util.function.Supplier<ActionExecution> factory) {
        return factory::get;
    }

    private static void requireFinitePosition(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Position coordinates must be finite");
        }
    }
}
