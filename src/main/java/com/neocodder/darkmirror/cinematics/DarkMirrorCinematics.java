package com.neocodder.darkmirror.cinematics;

import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;

/** NeoForge entry point for the standalone cinematics API mod. */
@Mod(DarkMirrorCinematics.MOD_ID)
public final class DarkMirrorCinematics {
    /** Mod identifier used for metadata, registration and network payload IDs. */
    public static final String MOD_ID = "neoscutapi";

    /**
     * Initializes the API's NeoForge event and networking listeners.
     *
     * @param modEventBus event bus provided by NeoForge
     */
    public DarkMirrorCinematics(IEventBus modEventBus) {
        CinematicsAPI.initialize(modEventBus);
    }
}
