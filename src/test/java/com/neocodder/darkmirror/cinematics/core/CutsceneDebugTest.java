package com.neocodder.darkmirror.cinematics.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.neocodder.darkmirror.cinematics.api.scene.SceneType;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class CutsceneDebugTest {
    @Test
    void resolvesQualifiedNamesAndOnlyUnambiguousShortNames() {
        ResourceLocation first = ResourceLocation.fromNamespaceAndPath("first", "intro");
        ResourceLocation second = ResourceLocation.fromNamespaceAndPath("second", "intro");
        assertEquals(first, CutsceneCommands.resolve("intro", List.of(first)));
        assertEquals(second, CutsceneCommands.resolve("second:intro", List.of(first, second)));
        assertThrows(IllegalArgumentException.class, () -> CutsceneCommands.resolve("intro", List.of(first, second)));
        assertThrows(IllegalArgumentException.class, () -> CutsceneCommands.resolve("missing", List.of(first)));
    }

    @Test
    void everyDebugEpisodeHasAUniqueNameAndAFixedPlaybackWindow() {
        var episodes = List.of(CutsceneDebug.Episode.values());
        assertEquals(episodes.size(), episodes.stream().map(CutsceneDebug.Episode::id).distinct().count());
        assertTrue(CutsceneDebug.EPISODE_TICKS >= 300 && CutsceneDebug.EPISODE_TICKS <= 500);
        assertEquals(100, CutsceneDebug.PAUSE_TICKS);
        for (var episode : episodes) {
            var scene = CutsceneDebug.scene(episode, Vec3.ZERO, 0,
                    ResourceLocation.withDefaultNamespace("overworld"));
            assertEquals(episode.id(), scene.id());
            assertEquals(episode.type(), scene.type());
        }
        assertEquals(SceneType.INTERACTIVE, CutsceneDebug.Episode.INTERACTIVE.type());
        assertEquals(SceneType.CINEMATIC, CutsceneDebug.Episode.CAMERA_FOLLOW.type());
        assertEquals(SceneType.CINEMATIC, CutsceneDebug.Episode.CAMERA_PATH.type());
    }
}
