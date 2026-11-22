package com.neocodder.darkmirror.cinematics.api;

/**
 * Client-only presentation features negotiated with the logical server.
 * Feature bit values are part of the network protocol and must remain stable.
 */
public enum ClientFeature {
    /** Screen fade overlay rendered by the API client. */
    SCREEN_FADE(1),
    /** Timed dialogue text overlay rendered by the API client. */
    DIALOGUE_OVERLAY(1 << 1),
    /** Independent camera, movement lock, and clean HUD mode for cinematic scenes. */
    CINEMATIC_CAMERA(1 << 2);

    private final int bit;

    ClientFeature(int bit) {
        this.bit = bit;
    }

    /**
     * Returns this feature's stable handshake bit.
     *
     * @return stable positive bit mask
     */
    public int bit() {
        return bit;
    }
}
