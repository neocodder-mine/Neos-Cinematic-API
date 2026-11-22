package com.neocodder.darkmirror.cinematics.api.actor;

import com.neocodder.darkmirror.cinematics.api.context.SceneContext;
import net.minecraft.world.entity.Entity;

/** A server-thread callback for custom scripted actor behavior. */
@FunctionalInterface
public interface ActorAction {
    /**
     * Executes a short, non-blocking actor operation.
     *
     * @param context live context for this action tick
     * @param actor actor entity
     */
    void execute(SceneContext context, Entity actor);
}
