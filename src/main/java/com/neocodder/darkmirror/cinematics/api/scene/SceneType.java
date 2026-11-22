package com.neocodder.darkmirror.cinematics.api.scene;

/** Controls whether participants retain normal gameplay control while a scene runs. */
public enum SceneType {
    /** Participants keep control of their characters, camera, and interfaces. */
    INTERACTIVE,
    /** Participants are locked in place while a separate cinematic camera is controlled by the scene. */
    CINEMATIC
}
