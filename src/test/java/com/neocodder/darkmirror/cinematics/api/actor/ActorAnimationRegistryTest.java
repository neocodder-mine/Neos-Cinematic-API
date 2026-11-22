package com.neocodder.darkmirror.cinematics.api.actor;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ActorAnimationRegistryTest {
    @Test
    void registersAdaptersByUniqueId() {
        ActorAnimationRegistry registry = new ActorAnimationRegistry();
        ActorAnimationAdapter adapter = new ActorAnimationAdapter() {
            @Override
            public void play(net.minecraft.world.entity.Entity actor, ResourceLocation animationId) {
            }

            @Override
            public void stop(net.minecraft.world.entity.Entity actor, ResourceLocation animationId) {
            }
        };
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test", "animations");

        registry.register(id, adapter);

        assertSame(adapter, registry.find(id).orElseThrow());
        assertThrows(IllegalStateException.class, () -> registry.register(id, adapter));
    }
}
