package com.neocodder.darkmirror.cinematics.api.scene;

import com.neocodder.darkmirror.cinematics.api.action.SceneAction;
import com.neocodder.darkmirror.cinematics.api.action.BuiltInActions;
import com.neocodder.darkmirror.cinematics.api.ClientFeature;
import com.neocodder.darkmirror.cinematics.api.context.SceneContext;
import com.neocodder.darkmirror.cinematics.api.action.ActorActions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

/** Fluent builder for an immutable {@link CinematicScene}; all durations use server ticks. */
public final class SceneBuilder {
    private final ResourceLocation id;
    private final List<SceneAction> actions = new ArrayList<>();
    private SceneType type = SceneType.INTERACTIVE;

    SceneBuilder(ResourceLocation id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    /**
     * Adds one required or optional action.
     *
     * @param action immutable action definition
     * @return this builder
     */
    public SceneBuilder action(SceneAction action) {
        actions.add(Objects.requireNonNull(action, "action"));
        return this;
    }

    /**
     * Adds a scripted entity actor and returns its fluent command builder.
     * Actors spawned this way are removed automatically when the scene terminates.
     *
     * @param actorId unique ID local to this scene definition
     * @param entityType registered entity type
     * @param dimension already loaded dimension
     * @param x spawn X coordinate
     * @param y spawn Y coordinate
     * @param z spawn Z coordinate
     * @return builder that appends commands for the spawned actor
     */
    public ActorBuilder spawnActor(String actorId, ResourceLocation entityType, ResourceLocation dimension,
                                   double x, double y, double z) {
        action(ActorActions.spawn(actorId, entityType, dimension, x, y, z));
        return new ActorBuilder(this, actorId);
    }

    /**
     * Returns a command builder for an actor previously spawned by this scene.
     *
     * @param actorId scene-local actor ID
     * @return actor command builder
     */
    public ActorBuilder actor(String actorId) {
        return new ActorBuilder(this, actorId);
    }

    /**
     * Selects whether participants retain normal gameplay control.
     *
     * @param type interactive or fully controlled cinematic mode
     * @return this builder
     */
    public SceneBuilder type(SceneType type) {
        this.type = Objects.requireNonNull(type, "type");
        return this;
    }

    /**
     * Adds a group whose member actions progress concurrently.
     *
     * @param actions non-empty action group
     * @return this builder
     */
    public SceneBuilder parallel(SceneAction... actions) {
        Objects.requireNonNull(actions, "actions");
        if (actions.length == 0) {
            throw new IllegalArgumentException("A parallel group must contain at least one action");
        }
        this.actions.add(BuiltInActions.parallel(Arrays.asList(actions)));
        return this;
    }

    /**
     * Adds a non-blocking server-tick delay.
     *
     * @param ticks delay length
     * @return this builder
     */
    public SceneBuilder waitTicks(int ticks) {
        return action(BuiltInActions.waitTicks(ticks));
    }

    /**
     * Adds a sound action addressed to online participants.
     *
     * @param sound registered sound event ID
     * @param volume sound volume
     * @param pitch sound pitch
     * @return this builder
     */
    public SceneBuilder playSound(ResourceLocation sound, float volume, float pitch) {
        return action(BuiltInActions.playSound(sound, volume, pitch));
    }

    /**
     * Adds vanilla title and subtitle packets with their vanilla fade timings.
     *
     * @param title title component
     * @param subtitle subtitle component
     * @param fadeInTicks fade-in duration
     * @param stayTicks visible duration
     * @param fadeOutTicks fade-out duration
     * @return this builder
     */
    public SceneBuilder title(Component title, Component subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        return action(BuiltInActions.title(title, subtitle, fadeInTicks, stayTicks, fadeOutTicks));
    }

    /**
     * Adds dialogue with a plain-message fallback for clients without the API.
     *
     * @param line dialogue line
     * @param durationTicks display duration
     * @return this builder
     */
    public SceneBuilder dialogue(Component line, int durationTicks) {
        return action(BuiltInActions.dialogue(line, durationTicks));
    }

    /**
     * Adds an optional client overlay fade from transparent to opaque.
     *
     * @param durationTicks fade duration
     * @return this builder
     */
    public SceneBuilder fadeOut(int durationTicks) {
        return action(BuiltInActions.fadeOut(durationTicks));
    }

    /**
     * Adds an optional client overlay fade from opaque to transparent.
     *
     * @param durationTicks fade duration
     * @return this builder
     */
    public SceneBuilder fadeIn(int durationTicks) {
        return action(BuiltInActions.fadeIn(durationTicks));
    }

    /**
     * Moves the independent cinematic camera smoothly to a world position and rotation.
     *
     * @param x target X coordinate
     * @param y target Y coordinate
     * @param z target Z coordinate
     * @param yaw target yaw
     * @param pitch target pitch
     * @param durationTicks movement duration
     * @return this builder
     */
    public SceneBuilder cameraMove(double x, double y, double z, float yaw, float pitch, int durationTicks) {
        return action(BuiltInActions.cameraMove(x, y, z, yaw, pitch, durationTicks));
    }

    /**
     * Makes the independent cinematic camera follow an entity in the participants' dimension.
     *
     * @param entityId runtime entity ID visible to the participants
     * @param offsetX camera X offset from the entity
     * @param offsetY camera Y offset from the entity
     * @param offsetZ camera Z offset from the entity
     * @param durationTicks follow duration
     * @return this builder
     */
    public SceneBuilder cameraFollowEntity(int entityId, double offsetX, double offsetY, double offsetZ,
                                           int durationTicks) {
        return action(BuiltInActions.cameraFollowEntity(
                entityId, offsetX, offsetY, offsetZ, durationTicks));
    }

    /**
     * Follows an entity selected at runtime, such as an NPC spawned by an earlier scene action.
     *
     * @param targetResolver server-thread resolver for the current target entity
     * @param offsetX camera X offset
     * @param offsetY camera Y offset
     * @param offsetZ camera Z offset
     * @param durationTicks follow duration
     * @return this builder
     */
    public SceneBuilder cameraFollowEntity(
            Function<SceneContext, ? extends Entity> targetResolver,
            double offsetX, double offsetY, double offsetZ, int durationTicks) {
        return action(BuiltInActions.cameraFollowEntity(
                targetResolver, offsetX, offsetY, offsetZ, durationTicks));
    }

    /**
     * Adds a short callback executed on the logical server thread.
     *
     * @param action callback that must not block
     * @return this builder
     */
    public SceneBuilder serverAction(Consumer<com.neocodder.darkmirror.cinematics.api.context.SceneContext> action) {
        return action(BuiltInActions.serverAction(action));
    }

    /**
     * Adds server-side entity creation in an already loaded dimension.
     *
     * @param entityType entity type ID
     * @param dimension loaded dimension ID
     * @param x target X coordinate
     * @param y target Y coordinate
     * @param z target Z coordinate
     * @return this builder
     */
    public SceneBuilder spawnEntity(ResourceLocation entityType, ResourceLocation dimension,
                                    double x, double y, double z) {
        return action(BuiltInActions.spawnEntity(entityType, dimension, x, y, z));
    }

    /**
     * Teleports connected participants on the server.
     *
     * @param dimension loaded destination dimension
     * @param x destination X coordinate
     * @param y destination Y coordinate
     * @param z destination Z coordinate
     * @param yaw destination yaw
     * @param pitch destination pitch
     * @return this builder
     */
    public SceneBuilder teleportParticipants(ResourceLocation dimension, double x, double y, double z,
                                             float yaw, float pitch) {
        return action(BuiltInActions.teleportParticipants(dimension, x, y, z, yaw, pitch));
    }

    /**
     * Adds removal of an entity UUID in the specified dimension.
     *
     * @param dimension loaded dimension ID
     * @param entityId entity UUID
     * @return this builder
     */
    public SceneBuilder removeEntity(ResourceLocation dimension, java.util.UUID entityId) {
        return action(BuiltInActions.removeEntity(dimension, entityId));
    }

    /**
     * Adds server particles of a registered simple particle type.
     *
     * @param particle particle type ID
     * @param dimension loaded dimension ID
     * @param x origin X coordinate
     * @param y origin Y coordinate
     * @param z origin Z coordinate
     * @param count particle count
     * @param speed spread speed
     * @return this builder
     */
    public SceneBuilder spawnParticles(ResourceLocation particle, ResourceLocation dimension,
                                       double x, double y, double z, int count, double speed) {
        return action(BuiltInActions.spawnParticles(particle, dimension, x, y, z, count, speed));
    }

    /**
     * Adds an action that completes the scene immediately.
     *
     * @return this builder
     */
    public SceneBuilder finish() {
        return action(BuiltInActions.finish());
    }

    /**
     * Creates the immutable scene definition.
     *
     * @return reusable scene definition
     */
    public CinematicScene build() {
        if (type != SceneType.CINEMATIC && actions.stream()
                .anyMatch(action -> action.requiredFeatures().contains(ClientFeature.CINEMATIC_CAMERA))) {
            throw new IllegalStateException("Camera actions require SceneBuilder.type(SceneType.CINEMATIC)");
        }
        return new CinematicScene(id, actions, type);
    }
}
