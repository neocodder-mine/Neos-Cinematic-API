# Neo's Cinematic API

A Java API for scripted cutscenes in Minecraft. Addons can register reusable
scenes that run on the server, with sequential or parallel actions, temporary
actors, dialogue, and an optional client camera.

| Requirement | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.265 |
| Java | 21 |
| Mod ID | `neoscutapi` |
| Author | NEOCODDER |

## Install

Install `neoscutapi-0.1.0.jar` alongside the addon that uses it. Independent
camera scenes require the API on the server and every participating client.
Interactive scenes can use vanilla presentation when a client lacks the API.

## Try the showcase

From an in-game operator account, stand on flat, open ground and run
`/cutscene debugg`. The showcase plays 19 episodes of about 20 seconds each,
with five-second gaps at 20 TPS. Run `/cutscene debugg stop` during a gap or an
interactive episode to stop it.

Use `/cutscene "namespace:path"` to play a registered scene. The command suggests
scene IDs. For a quick camera test, run `/cutscene "neoscutapi:debug/camera_path"`.

## Build

Use a JDK 21:

```sh
sh ./gradlew build
```

On Windows, run `.\gradlew.bat build` instead. The build runs the unit tests and
writes the mod, sources, and Javadoc JARs to `build/libs`. Use `runClient` or
`runServer` for development launches.

## Documentation

- [Addon integration guide](docs/USAGE.md) explains setup, scene registration,
  timing, and lifecycle events.
- [Camera and actor examples](docs/USAGE.md#scripted-actors) show how to stage
  temporary characters, follow them with the camera, and connect animations.
- [Command reference](docs/USAGE.md#test-and-troubleshoot) lists playback and
  showcase commands, plus troubleshooting steps.

## License

Licensed under the [MIT License](LICENSE).
