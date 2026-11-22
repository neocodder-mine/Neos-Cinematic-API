package com.neocodder.darkmirror.cinematics.api.scene;

import com.neocodder.darkmirror.cinematics.api.action.ActorActions;
import com.neocodder.darkmirror.cinematics.api.actor.ActorAction;
import com.neocodder.darkmirror.cinematics.api.context.SceneContext;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/**
 * Appends sequential server-side commands for one scene-local entity actor.
 *
 * <p>Commands are added to the parent scene immediately. Call {@link #endActor()}
 * when switching back to the scene builder.</p>
 */
public final class ActorBuilder {
    private final SceneBuilder scene;
    private final String actorId;

    ActorBuilder(SceneBuilder scene, String actorId) {
        this.scene = Objects.requireNonNull(scene, "scene");
        this.actorId = Objects.requireNonNull(actorId, "actorId");
        if (actorId.isBlank()) {
            throw new IllegalArgumentException("Actor ID cannot be blank");
        }
    }

    /**
     * Moves smoothly toward a world-space waypoint at blocks per server tick.
     *
     * @param x destination X
     * @param y destination Y
     * @param z destination Z
     * @param blocksPerTick maximum travel distance per tick
     * @return this builder
     */
    public ActorBuilder moveTo(double x, double y, double z, double blocksPerTick) {
        scene.action(ActorActions.moveTo(actorId, x, y, z, blocksPerTick));
        return this;
    }

    /**
     * Turns the actor to face a world-space point.
     *
     * @param x target X
     * @param y target Y
     * @param z target Z
     * @return this builder
     */
    public ActorBuilder lookAt(double x, double y, double z) {
        scene.action(ActorActions.lookAt(actorId, x, y, z));
        return this;
    }

    /**
     * Turns the actor toward the nearest online scene participant.
     *
     * @return this builder
     */
    public ActorBuilder lookAtPlayer() {
        scene.action(ActorActions.lookAtPlayer(actorId));
        return this;
    }

    /**
     * Turns the actor toward an entity resolved on the server when this command runs.
     *
     * @param target server-thread target resolver
     * @return this builder
     */
    public ActorBuilder lookAt(Function<SceneContext, ? extends Entity> target) {
        scene.action(ActorActions.lookAtEntity(actorId, target));
        return this;
    }

    /**
     * Moves toward the nearest online scene participant until within the given distance.
     *
     * @param stopDistance distance at which the command completes
     * @param blocksPerTick maximum movement per server tick
     * @return this builder
     */
    public ActorBuilder approachPlayer(double stopDistance, double blocksPerTick) {
        Function<SceneContext, Entity> target = context -> {
            Entity actor = context.actors().find(actorId).orElse(null);
            return actor == null ? null : context.onlinePlayers().stream()
                    .min(java.util.Comparator.comparingDouble(player -> player.distanceToSqr(actor)))
                    .orElse(null);
        };
        scene.action(ActorActions.approach(actorId, target, stopDistance, blocksPerTick));
        return this;
    }

    /**
     * Moves toward an entity resolved on the server until within the given distance.
     *
     * @param target server-thread target resolver
     * @param stopDistance distance at which the command completes
     * @param blocksPerTick maximum movement per server tick
     * @return this builder
     */
    public ActorBuilder approach(Function<SceneContext, ? extends Entity> target,
                                 double stopDistance, double blocksPerTick) {
        scene.action(ActorActions.approach(actorId, target, stopDistance, blocksPerTick));
        return this;
    }

    /**
     * Performs a deterministic vertical arc and returns to the starting height.
     *
     * @param height maximum height above the starting position
     * @param durationTicks total arc duration
     * @return this builder
     */
    public ActorBuilder jump(double height, int durationTicks) {
        scene.action(ActorActions.jump(actorId, height, durationTicks));
        return this;
    }

    /**
     * Clears the actor's current velocity between scripted route segments.
     *
     * @return this builder
     */
    public ActorBuilder stop() {
        scene.action(ActorActions.stop(actorId));
        return this;
    }

    /**
     * Starts an animation using an addon-provided animation adapter.
     *
     * @param adapterId registered adapter ID
     * @param animationId adapter-specific animation ID
     * @return this builder
     */
    public ActorBuilder playAnimation(ResourceLocation adapterId, ResourceLocation animationId) {
        scene.action(ActorActions.startAnimation(actorId, adapterId, animationId));
        return this;
    }

    /**
     * Starts an animation, waits the requested duration, then stops it.
     * Cancellation also calls the adapter's stop method.
     *
     * @param adapterId registered adapter ID
     * @param animationId adapter-specific animation ID
     * @param durationTicks animation duration in server ticks
     * @return this builder
     */
    public ActorBuilder playAnimation(ResourceLocation adapterId, ResourceLocation animationId,
                                      int durationTicks) {
        scene.action(ActorActions.playAnimation(actorId, adapterId, animationId, durationTicks));
        return this;
    }

    /**
     * Stops an animation through its registered adapter.
     *
     * @param adapterId registered adapter ID
     * @param animationId adapter-specific animation ID
     * @return this builder
     */
    public ActorBuilder stopAnimation(ResourceLocation adapterId, ResourceLocation animationId) {
        scene.action(ActorActions.stopAnimation(actorId, adapterId, animationId));
        return this;
    }

    /**
     * Adds a dialogue overlay, gated on this actor still existing.
     *
     * @param line dialogue line
     * @param durationTicks display duration
     * @return this builder
     */
    public ActorBuilder say(Component line, int durationTicks) {
        scene.action(ActorActions.say(actorId, line, durationTicks));
        return this;
    }

    /**
     * Adds a non-blocking delay before the next actor or scene command.
     *
     * @param ticks delay in server ticks
     * @return this builder
     */
    public ActorBuilder waitTicks(int ticks) {
        scene.waitTicks(ticks);
        return this;
    }

    /**
     * Executes custom server-side behavior for the actor.
     * Keep callbacks short; they run on the server tick thread.
     *
     * @param action callback to run
     * @return this builder
     */
    public ActorBuilder action(ActorAction action) {
        scene.action(ActorActions.custom(actorId, action));
        return this;
    }

    /**
     * Runs a callback with this actor and another previously spawned scene actor.
     *
     * @param targetActorId other scene-local actor ID
     * @param interaction interaction callback
     * @return this builder
     */
    public ActorBuilder interactWith(String targetActorId, BiConsumer<Entity, Entity> interaction) {
        Objects.requireNonNull(targetActorId, "targetActorId");
        Objects.requireNonNull(interaction, "interaction");
        return action((context, actor) -> {
            Entity target = context.actors().find(targetActorId)
                    .orElseThrow(() -> new IllegalStateException("Target actor is not available: " + targetActorId));
            interaction.accept(actor, target);
        });
    }

    /**
     * Changes mob AI and gravity settings; scripted actors default to both disabled.
     *
     * @param noAi whether mob AI should be disabled
     * @param noGravity whether gravity should be disabled
     * @return this builder
     */
    public ActorBuilder physics(boolean noAi, boolean noGravity) {
        return action((context, actor) -> {
            actor.setNoGravity(noGravity);
            if (actor instanceof Mob mob) {
                mob.setNoAi(noAi);
            }
        });
    }

    /**
     * Leaves this actor in the world when the scene terminates.
     *
     * @return this builder
     */
    public ActorBuilder keepAfterScene() {
        scene.action(ActorActions.persist(actorId, false));
        return this;
    }

    /**
     * Removes this actor immediately. Temporary actors are also removed on scene termination.
     *
     * @return this builder
     */
    public ActorBuilder despawn() {
        scene.action(ActorActions.despawn(actorId));
        return this;
    }

    /**
     * Returns to the parent scene builder so other scene actions can be appended.
     *
     * @return parent scene builder
     */
    public SceneBuilder endActor() {
        return scene;
    }

    /**
     * Returns this actor's scene-local ID.
     *
     * @return scene-local actor ID
     */
    public String actorId() {
        return actorId;
    }
}
