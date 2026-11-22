# Addon integration guide

This guide targets NEO's cinematic API 0.1.0 on Minecraft 1.21.1, NeoForge
21.1.265, and Java 21. The mod ID is `neoscutapi`.

## Add the dependency

Place `neoscutapi-0.1.0.jar` in the addon project's `libs` directory and add it to
the Gradle dependencies.

```groovy
dependencies {
    implementation files("libs/neoscutapi-0.1.0.jar")
}
```

Declare the dependency in the addon's `META-INF/neoforge.mods.toml`. Replace
`example_addon` with your addon's mod ID.

```toml
[[dependencies.example_addon]]
modId = "neoscutapi"
type = "required"
versionRange = "[0.1.0,0.2)"
ordering = "AFTER"
side = "BOTH"
```

For normal launches, put the API JAR in the server's `mods` directory. Install
it on each client whose addon requires it or who participates in camera scenes.
If you build from source and use `publishToMavenLocal`, the coordinates are
`com.neocodder.darkmirror.cinematics:neoscutapi:0.1.0`.

## Register your first scene

Register definitions once when your addon initializes. The command
`/cutscene "example_addon:intro"` then plays this scene for the operator who runs
it. In an existing addon, put the registration in your current mod entry point.

```java
package com.example.addon;

import com.neocodder.darkmirror.cinematics.CinematicsAPI;
import com.neocodder.darkmirror.cinematics.api.scene.CinematicScene;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.common.Mod;

@Mod("example_addon")
public final class ExampleAddon {
    public ExampleAddon() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("example_addon", "intro");
        CinematicsAPI.registerScene(CinematicScene.builder(id)
                .title(Component.literal("Observatory"),
                        Component.literal("The ritual begins"), 5, 40, 10)
                .waitTicks(20)
                .dialogue(Component.literal("Guide: Look toward the tower."), 60)
                .waitTicks(60)
                .build());
    }
}
```

`INTERACTIVE` is the default mode. Players keep their gameplay controls and
normal camera. When the presentation channel is unavailable, dialogue falls
back to a system message and fades are skipped. Sound and titles use Minecraft's
built-in presentation.

## Play and manage scenes

Read and modify scene instances on the main server thread. Use a namespaced
`ResourceLocation` to identify the scene definition and the UUID returned by
`start` to identify an individual run.

```java
var scenes = CinematicsAPI.scenes();
var instanceId = scenes.start(server, id, List.of(player.getUUID()));

// Later, on the server thread
boolean cancelled = scenes.stop(server, instanceId, "Quest cancelled");
var result = scenes.result(server, instanceId);
```

- Pass at least one connected player's UUID. The same definition can play
  repeatedly. Each run gets its own action state.
- Use `addParticipant` and `removeParticipant` to change the audience. Previously
  sent effects do not replay for late joiners. Losing the last participant
  cancels the scene.
- `find` and `activeScenes` return immutable snapshots of active runs.
  `result` returns a terminal outcome. The server keeps up to 256 outcomes
  in memory.
- Subscribe to `SceneEvent` on `NeoForge.EVENT_BUS`. Use `COMPLETED` for quest
  progression and handle `CANCELLED` or `FAILED` as needed. The result is
  available during the terminal event. The first actions can run inside
  `start`, so an immediate scene can emit its events before `start` returns.

## Time and presentation

| Action | When it advances |
| --- | --- |
| `waitTicks(ticks)` | After the specified delay. |
| `title`, `dialogue`, `playSound` | After sending the effect. Add an explicit wait to keep the scene on that cue. |
| `fadeOut`, `fadeIn` | After the effect's timer. Pair them for a transition. |
| `cameraMove`, `cameraFollowEntity` | After the requested camera duration. |
| `parallel(actionA, actionB)` | When the group finishes. Optional failures allow siblings to continue. |
| `finish()` | Completes the scene immediately. The end of the action list also completes the scene. |

At 20 TPS, 20 ticks are approximately one second. Server lag lengthens
tick-based actions. Client presentation timing can vary with network delay.

For a cue that should play during a camera move, put both actions in the same
`parallel` group.

```java
CinematicScene shot = CinematicScene.builder(shotId)
        .type(SceneType.CINEMATIC)
        .parallel(
                BuiltInActions.cameraMove(12.5, 82.0, -4.5, 90, 10, 60),
                BuiltInActions.dialogue(Component.literal("The tower is ahead."), 60))
        .waitTicks(20)
        .build();
```

In `CINEMATIC`, the client uses a separate camera and hides the HUD while
gameplay controls are locked. The player remains at their server-side position.
One cinematic instance can own a player's camera at a time. To chain camera
scenes for the same player from a terminal event, schedule the next start for
the following server tick.

## Scripted actors

`spawnActor` creates a temporary entity with a scene-local ID and returns an
`ActorBuilder`. `endActor` returns to the scene builder. Use `actor(id)` to add
more commands for an existing actor. Replace the example coordinates and
dimension with a loaded area near the audience.

```java
CinematicScene follow = CinematicScene.builder(followId)
        .type(SceneType.CINEMATIC)
        .spawnActor("guide", ResourceLocation.withDefaultNamespace("villager"),
                ResourceLocation.withDefaultNamespace("overworld"), 10.5, 80.0, -4.5)
            .lookAtPlayer()
            .say(Component.literal("Guide: This way."), 60)
            .endActor()
        .parallel(
                BuiltInActions.cameraFollowEntity(
                        context -> context.actors().find("guide").orElse(null),
                        -3, 2, 0, 100),
                ActorActions.moveTo("guide", 14.5, 80.0, -4.5, 0.05))
        .build();
```

The camera resolver runs when its action begins. The entity must be in the
participants' dimension and tracked by their clients. A camera move does not
load distant chunks. `moveTo` is scripted movement, so check routes for solid
blocks and loaded chunks.

- Actors can turn, approach a target, jump, speak, and perform callbacks or
  interactions. `physics(noAi, noGravity)` controls AI and gravity.
- Temporary actors are removed on completion, cancellation, or failure.
  `despawn()` removes an actor early. `keepAfterScene()` leaves it in the world.
- Register an `ActorAnimationAdapter` through `CinematicsAPI.actorAnimations()`
  to connect `playAnimation` and `stopAnimation` to a mod's animation system.
  The adapter runs on the server and is responsible for synchronizing its own
  animations.

## Extend the action set

Use `serverAction(context -> ...)` for short server-thread callbacks. For a
multi-tick action, implement `SceneAction` and create a fresh `ActionExecution`
in `createExecution()`. Store mutable state in the execution. Use `cancel` to
release resources when an action is interrupted.

`ActionResult` can keep an action running, advance the sequence, complete the
scene, or fail with a diagnostic message. `optional()` allows an action to be
skipped on failure. `requiredFeatures()` declares a client capability
requirement. Use `CinematicsAPI.customActions()` to register parameterized
action factories by ID.

Use `SceneContext` and resolved player or world objects only during the current
callback. The supported integration surface is `CinematicsAPI` and the
`com.neocodder.darkmirror.cinematics.api` packages. Generated Javadoc covers
the public methods and their arguments.

## Test and troubleshoot

| Command | Result |
| --- | --- |
| `/cutscene "example_addon:intro"` | Plays a registered scene by full ID. A unique short path also works. |
| `/cutscene debugg` | Plays all 19 showcase episodes with five-second gaps at 20 TPS. |
| `/cutscene debugg stop` | Stops the showcase. Run it between episodes or during an interactive episode. |
| `/cutscene "neoscutapi:debug/interactive"` | Plays the episode where you follow the guide and crouch to reply. |
| `/cutscene "neoscutapi:debug/camera_follow"` | Plays the camera-follow episode. |
| `/cutscene "neoscutapi:debug/camera_path"` | Plays the camera flythrough without spawned actors. |

Run player commands from an in-game operator account. For camera scenes,
check the client mod and the player's camera capability. If a target is
missing, check its ID, dimension, chunk loading, and client tracking range.
When a scene stops unexpectedly, inspect the terminal `SceneEvent` and
`SceneResult`. Test multiclient camera behavior on a dedicated server before
shipping an addon.

Scenes are authored in Java. This version provides no visual editor,
external scene-file format, pause/resume command, or persistence across a
server restart.
