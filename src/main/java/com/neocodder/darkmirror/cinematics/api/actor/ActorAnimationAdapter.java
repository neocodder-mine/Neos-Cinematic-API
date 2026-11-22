package com.neocodder.darkmirror.cinematics.api.actor;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * Adapter between Cinematics actor commands and a mod's animation system.
 *
 * <p>Methods run on the logical server thread. Implementations are responsible
 * for synchronizing custom animations to clients when their animation library
 * requires it.</p>
 */
public interface ActorAnimationAdapter {
    /**
     * Starts an animation on an actor.
     *
     * @param actor server-side actor entity
     * @param animationId animation identifier understood by this adapter
     */
    void play(Entity actor, ResourceLocation animationId);

    /**
     * Stops an animation on an actor.
     *
     * @param actor server-side actor entity
     * @param animationId animation identifier understood by this adapter
     */
    void stop(Entity actor, ResourceLocation animationId);
}
