package com.neocodder.darkmirror.cinematics.api.action;

import com.neocodder.darkmirror.cinematics.CinematicsAPI;
import com.neocodder.darkmirror.cinematics.api.actor.ActorAction;
import com.neocodder.darkmirror.cinematics.api.actor.ActorAnimationAdapter;
import com.neocodder.darkmirror.cinematics.api.context.SceneContext;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;

/** Factories used by the fluent scripted-actor builder. */
public final class ActorActions {
    private ActorActions() {
    }

    /**
     * Creates and registers a temporary scene actor in a loaded dimension and chunk.
     *
     * @param actorId scene-local actor ID
     * @param entityTypeId registered entity type ID
     * @param dimension loaded dimension ID
     * @param x spawn X coordinate
     * @param y spawn Y coordinate
     * @param z spawn Z coordinate
     * @return spawn action
     */
    public static SceneAction spawn(String actorId, ResourceLocation entityTypeId, ResourceLocation dimension,
                                    double x, double y, double z) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(entityTypeId, "entityTypeId");
        Objects.requireNonNull(dimension, "dimension");
        requirePosition(x, y, z);
        if (actorId.isBlank()) {
            throw new IllegalArgumentException("Actor ID cannot be blank");
        }
        return instant(context -> {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimension);
            Level level = context.level(key);
            if (!(level instanceof ServerLevel serverLevel)) {
                return ActionResult.failed("Actor dimension is not loaded: " + dimension);
            }
            BlockPos pos = BlockPos.containing(x, y, z);
            if (!serverLevel.hasChunkAt(pos)) {
                return ActionResult.failed("Actor spawn chunk is not loaded: " + pos);
            }
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entityTypeId).orElse(null);
            if (type == null) {
                return ActionResult.failed("Unknown actor entity type: " + entityTypeId);
            }
            Entity entity = type.create(serverLevel);
            if (entity == null) {
                return ActionResult.failed("Could not create actor entity: " + entityTypeId);
            }
            entity.moveTo(x, y, z, entity.getYRot(), entity.getXRot());
            entity.setNoGravity(true);
            if (entity instanceof Mob mob) {
                mob.setNoAi(true);
            }
            if (!serverLevel.addFreshEntity(entity)) {
                return ActionResult.failed("World rejected actor spawn: " + entityTypeId);
            }
            try {
                context.actors().register(actorId, entity, true);
            } catch (RuntimeException exception) {
                entity.discard();
                return ActionResult.failed("Could not register actor " + actorId + ": " + exception.getMessage());
            }
            return ActionResult.completed();
        });
    }

    /**
     * Moves an actor toward a fixed waypoint at the requested maximum speed.
     *
     * @param actorId scene-local actor ID
     * @param x destination X
     * @param y destination Y
     * @param z destination Z
     * @param speed maximum blocks per server tick
     * @return movement action
     */
    public static SceneAction moveTo(String actorId, double x, double y, double z, double speed) {
        Objects.requireNonNull(actorId, "actorId");
        requirePosition(x, y, z);
        requirePositiveFinite(speed, "speed");
        return new SceneAction() {
            @Override
            public ActionExecution createExecution() {
                return context -> {
                    Entity actor = actor(context, actorId);
                    if (actor == null) {
                        return ActionResult.failed("Actor is not available: " + actorId);
                    }
                    double dx = x - actor.getX();
                    double dy = y - actor.getY();
                    double dz = z - actor.getZ();
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (distance > 0.0) {
                        face(actor, dx, dy, dz);
                    }
                    if (distance <= speed) {
                        if (!actor.level().hasChunkAt(BlockPos.containing(x, y, z))) {
                            return ActionResult.failed("Actor destination chunk is not loaded");
                        }
                        actor.setPos(x, y, z);
                        return ActionResult.completed();
                    }
                    double nx = actor.getX() + dx / distance * speed;
                    double ny = actor.getY() + dy / distance * speed;
                    double nz = actor.getZ() + dz / distance * speed;
                    if (!actor.level().hasChunkAt(BlockPos.containing(nx, ny, nz))) {
                        return ActionResult.failed("Actor route reached an unloaded chunk");
                    }
                    if (canStepUp(actor, nx, ny, nz)) {
                        ny += 1.0;
                    }
                    actor.setPos(nx, ny, nz);
                    return ActionResult.running();
                };
            }
        };
    }

    /**
     * Turns an actor toward a world-space point.
     *
     * @param actorId scene-local actor ID
     * @param x target X
     * @param y target Y
     * @param z target Z
     * @return look action
     */
    public static SceneAction lookAt(String actorId, double x, double y, double z) {
        Objects.requireNonNull(actorId, "actorId");
        requirePosition(x, y, z);
        return instant(context -> {
            Entity actor = actor(context, actorId);
            if (actor == null) {
                return ActionResult.failed("Actor is not available: " + actorId);
            }
            face(actor, x - actor.getX(), y - actor.getEyeY(), z - actor.getZ());
            return ActionResult.completed();
        });
    }

    /**
     * Turns an actor toward its nearest online participant.
     *
     * @param actorId scene-local actor ID
     * @return look action
     */
    public static SceneAction lookAtPlayer(String actorId) {
        Objects.requireNonNull(actorId, "actorId");
        return instant(context -> {
            Entity actor = actor(context, actorId);
            if (actor == null) {
                return ActionResult.failed("Actor is not available: " + actorId);
            }
            var target = context.onlinePlayers().stream()
                    .min(java.util.Comparator.comparingDouble(player ->
                            player.distanceToSqr(actor)));
            if (target.isEmpty()) {
                return ActionResult.failed("No online scene participant to look at");
            }
            Entity player = target.get();
            face(actor, player.getX() - actor.getX(), player.getEyeY() - actor.getEyeY(),
                    player.getZ() - actor.getZ());
            return ActionResult.completed();
        });
    }

    /**
     * Moves an actor toward an entity resolved on each server tick.
     *
     * @param actorId scene-local actor ID
     * @param targetResolver server-thread target resolver
     * @param stopDistance distance at which movement completes
     * @param speed maximum blocks per server tick
     * @return approach action
     */
    public static SceneAction approach(String actorId, Function<SceneContext, ? extends Entity> targetResolver,
                                       double stopDistance, double speed) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(targetResolver, "targetResolver");
        requirePositiveFinite(stopDistance, "stopDistance");
        requirePositiveFinite(speed, "speed");
        return new SceneAction() {
            @Override
            public ActionExecution createExecution() {
                return context -> {
                    Entity actor = actor(context, actorId);
                    Entity target = targetResolver.apply(context);
                    if (actor == null || target == null || target.isRemoved()) {
                        return ActionResult.failed("Actor or approach target is not available");
                    }

                    if (actor.level() != target.level()) {
                        return ActionResult.failed("Actor and approach target are in different dimensions");
                    }
                    double dx = target.getX() - actor.getX();
                    double dy = target.getY() - actor.getY();
                    double dz = target.getZ() - actor.getZ();
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (distance > 0.0) {
                        face(actor, dx, dy, dz);
                    }
                    if (distance <= stopDistance) {
                        return ActionResult.completed();
                    }
                    double travel = Math.min(speed, distance - stopDistance);
                    double nx = actor.getX() + dx / distance * travel;
                    double ny = actor.getY() + dy / distance * travel;
                    double nz = actor.getZ() + dz / distance * travel;
                    if (!actor.level().hasChunkAt(BlockPos.containing(nx, ny, nz))) {
                        return ActionResult.failed("Actor route reached an unloaded chunk");
                    }
                    actor.setPos(nx, ny, nz);
                    return ActionResult.running();
                };
            }
        };
    }

    /**
     * Turns an actor toward an entity resolved when this action runs.
     *
     * @param actorId scene-local actor ID
     * @param targetResolver server-thread target resolver
     * @return look action
     */
    public static SceneAction lookAtEntity(String actorId,
                                           Function<SceneContext, ? extends Entity> targetResolver) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(targetResolver, "targetResolver");
        return instant(context -> {
            Entity actor = actor(context, actorId);
            Entity target = targetResolver.apply(context);
            if (actor == null || target == null || target.isRemoved()) {
                return ActionResult.failed("Actor or look-at target is not available");
            }
            face(actor, target.getX() - actor.getX(), target.getEyeY() - actor.getEyeY(),
                    target.getZ() - actor.getZ());
            return ActionResult.completed();
        });
    }

    /**
     * Creates a deterministic vertical jump arc.
     *
     * @param actorId scene-local actor ID
     * @param height maximum height above the start point
     * @param durationTicks total arc duration
     * @return jump action
     */
    public static SceneAction jump(String actorId, double height, int durationTicks) {
        Objects.requireNonNull(actorId, "actorId");
        requirePositiveFinite(height, "height");
        if (durationTicks < 2) {
            throw new IllegalArgumentException("Jump duration must be at least 2 ticks");
        }
        return new SceneAction() {
            @Override
            public ActionExecution createExecution() {
                return new ActionExecution() {
                    private int elapsed;
                    private double startY;

                    @Override
                    public ActionResult tick(SceneContext context) {
                        Entity actor = actor(context, actorId);
                        if (actor == null) {
                            return ActionResult.failed("Actor is not available: " + actorId);
                        }
                        if (elapsed == 0) {
                            startY = actor.getY();
                        }
                        elapsed++;
                        double progress = (double) elapsed / durationTicks;
                        double y = startY + Math.sin(Math.PI * progress) * height;
                        actor.setPos(actor.getX(), y, actor.getZ());
                        return elapsed >= durationTicks
                                ? ActionResult.completed()
                                : ActionResult.running();
                    }
                };
            }
        };
    }

    /**
     * Starts an adapter animation and stops it after the specified duration.
     *
     * @param actorId scene-local actor ID
     * @param adapterId registered adapter ID
     * @param animationId adapter-specific animation ID
     * @param durationTicks animation duration in server ticks
     * @return timed animation action
     */
    public static SceneAction playAnimation(String actorId, ResourceLocation adapterId,
                                            ResourceLocation animationId, int durationTicks) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(adapterId, "adapterId");
        Objects.requireNonNull(animationId, "animationId");
        if (durationTicks < 1) {
            throw new IllegalArgumentException("Animation duration must be at least one tick");
        }
        return animation(actorId, adapterId, animationId, durationTicks);
    }

    /**
     * Starts an adapter animation that remains active until stopped or scene cleanup.
     *
     * @param actorId scene-local actor ID
     * @param adapterId registered adapter ID
     * @param animationId adapter-specific animation ID
     * @return animation start action
     */
    public static SceneAction startAnimation(String actorId, ResourceLocation adapterId,
                                              ResourceLocation animationId) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(adapterId, "adapterId");
        Objects.requireNonNull(animationId, "animationId");
        return animation(actorId, adapterId, animationId, 0);
    }

    /**
     * Stops the specified animation through its registered adapter.
     *
     * @param actorId scene-local actor ID
     * @param adapterId registered adapter ID
     * @param animationId adapter-specific animation ID
     * @return animation stop action
     */
    public static SceneAction stopAnimation(String actorId, ResourceLocation adapterId,
                                            ResourceLocation animationId) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(adapterId, "adapterId");
        Objects.requireNonNull(animationId, "animationId");
        return instant(context -> {
            Entity actor = actor(context, actorId);
            ActorAnimationAdapter adapter = CinematicsAPI.actorAnimations().find(adapterId).orElse(null);
            if (actor == null || adapter == null) {
                return ActionResult.failed(actor == null
                        ? "Actor is not available: " + actorId
                        : "Unknown actor animation adapter: " + adapterId);
            }
            if (!context.actors().stopAnimation(actorId, adapterId, animationId)) {
                adapter.stop(actor, animationId);
            }
            return ActionResult.completed();
        });
    }

    /**
     * Shows dialogue while verifying that its speaking actor still exists.
     *
     * @param actorId scene-local actor ID
     * @param line dialogue component
     * @param durationTicks display duration
     * @return dialogue action
     */
    public static SceneAction say(String actorId, Component line, int durationTicks) {
        Objects.requireNonNull(actorId, "actorId");
        SceneAction dialogue = BuiltInActions.dialogue(line, durationTicks);
        return () -> {
            ActionExecution execution = dialogue.createExecution();
            return context -> actor(context, actorId) == null
                    ? ActionResult.failed("Actor is not available: " + actorId)
                    : execution.tick(context);
        };
    }

    /**
     * Runs a short custom server callback for an actor.
     *
     * @param actorId scene-local actor ID
     * @param action callback to execute
     * @return callback action
     */
    public static SceneAction custom(String actorId, ActorAction action) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(action, "action");
        return instant(context -> {
            Entity actor = actor(context, actorId);
            if (actor == null) {
                return ActionResult.failed("Actor is not available: " + actorId);
            }
            action.execute(context, actor);
            return ActionResult.completed();
        });
    }

    /**
     * Removes an actor and discards its entity.
     *
     * @param actorId scene-local actor ID
     * @return despawn action
     */
    public static SceneAction despawn(String actorId) {
        Objects.requireNonNull(actorId, "actorId");
        return instant(context -> context.actors().despawn(actorId)
                ? ActionResult.completed()
                : ActionResult.failed("Actor is not available: " + actorId));
    }

    /**
     * Sets whether an actor is removed automatically when its scene terminates.
     *
     * @param actorId scene-local actor ID
     * @param temporary true to remove at termination
     * @return persistence action
     */
    public static SceneAction persist(String actorId, boolean temporary) {
        Objects.requireNonNull(actorId, "actorId");
        return instant(context -> {
            if (context.actors().find(actorId).isEmpty()) {
                return ActionResult.failed("Actor is not available: " + actorId);
            }
            context.actors().setTemporary(actorId, temporary);
            return ActionResult.completed();
        });
    }

    /**
     * Clears an actor's current velocity.
     *
     * @param actorId scene-local actor ID
     * @return stop action
     */
    public static SceneAction stop(String actorId) {
        Objects.requireNonNull(actorId, "actorId");
        return instant(context -> {
            Entity actor = actor(context, actorId);
            if (actor == null) {
                return ActionResult.failed("Actor is not available: " + actorId);
            }
            actor.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            return ActionResult.completed();
        });
    }

    private static SceneAction animation(String actorId, ResourceLocation adapterId,
                                         ResourceLocation animationId, int durationTicks) {
        return new SceneAction() {
            @Override
            public ActionExecution createExecution() {
                return new ActionExecution() {
                    private int elapsed;
                    private Runnable stopPlayback;

                    @Override
                    public ActionResult tick(SceneContext context) {
                        if (stopPlayback == null) {
                            Entity currentActor = actor(context, actorId);
                            ActorAnimationAdapter adapter = CinematicsAPI.actorAnimations().find(adapterId).orElse(null);
                            if (currentActor == null || adapter == null) {
                                return ActionResult.failed(currentActor == null
                                        ? "Actor is not available: " + actorId
                                        : "Unknown actor animation adapter: " + adapterId);
                            }
                            stopPlayback = context.actors().startTrackedAnimation(
                                    actorId, adapterId, animationId, adapter);
                        }
                        if (durationTicks == 0) {
                            return ActionResult.completed();
                        }
                        elapsed++;
                        if (elapsed >= durationTicks) {
                            stopPlayback.run();
                            return ActionResult.completed();
                        }
                        return ActionResult.running();
                    }

                    @Override
                    public void cancel(SceneContext context) {
                        if (stopPlayback != null) {
                            stopPlayback.run();
                        }
                    }
                };
            }
        };
    }

    private static SceneAction instant(java.util.function.Function<SceneContext, ActionResult> operation) {
        return () -> operation::apply;
    }

    private static Entity actor(SceneContext context, String actorId) {
        return context.actors().find(actorId).orElse(null);
    }

    private static void face(Entity entity, double dx, double dy, double dz) {
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        entity.setYRot(yaw);
        entity.setYHeadRot(yaw);
        entity.setXRot(pitch);
    }

    private static boolean canStepUp(Entity actor, double x, double y, double z) {
        BlockPos feet = BlockPos.containing(x, y, z);
        BlockPos head = feet.above();
        return actor.level().getBlockState(feet).isSolid()
                && actor.level().getBlockState(head).getCollisionShape(actor.level(), head).isEmpty()
                && actor.level().getBlockState(head.above()).getCollisionShape(actor.level(), head.above()).isEmpty();
    }

    private static void requirePosition(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Actor coordinates must be finite");
        }
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and greater than zero");
        }
    }
}
