package com.neocodder.darkmirror.cinematics.core;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.neocodder.darkmirror.cinematics.CinematicsAPI;
import com.neocodder.darkmirror.cinematics.api.ClientFeature;
import com.neocodder.darkmirror.cinematics.api.scene.SceneState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class CutsceneCommands {
    private static final CutsceneDebug.Episode[] EPISODES = CutsceneDebug.Episode.values();
    private final DefaultSceneManager scenes;
    private final Map<MinecraftServer, Map<UUID, DebugRun>> runs = new HashMap<>();

    public CutsceneCommands(DefaultSceneManager scenes, IEventBus gameBus) {
        this.scenes = scenes;
        CutsceneDebug.registerDemoExtensions();
        gameBus.addListener(this::registerCommands);
        gameBus.addListener(this::tick);
        gameBus.addListener(this::onServerStopping);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("cutscene")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("debugg")
                        .executes(context -> startDebug(context.getSource()))
                        .then(Commands.literal("stop").executes(context -> stopDebug(context.getSource()))))
                .then(Commands.argument("scene", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            String prefix = builder.getRemainingLowerCase();
                            sceneIds().map(ResourceLocation::toString).sorted().distinct()
                                    .map(StringArgumentType::escapeIfRequired)
                                    .filter(option -> option.startsWith(prefix)
                                            || option.startsWith("\"" + prefix))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> play(context.getSource(),
                                StringArgumentType.getString(context, "scene")))));
    }

    private int play(CommandSourceStack source, String name) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        try {
            if (runs.getOrDefault(server, Map.of()).containsKey(player.getUUID())) {
                throw new IllegalStateException("Finish the showcase or run /cutscene debugg stop first.");
            }
            ResourceLocation id = resolve(name, sceneIds().distinct().toList());
            UUID instanceId = CinematicsAPI.findScene(id).isPresent()
                    ? scenes.start(server, id, List.of(player.getUUID()))
                    : startEpisode(server, player, Stream.of(EPISODES)
                            .filter(episode -> episode.id().equals(id)).findFirst().orElseThrow());
            var result = scenes.result(server, instanceId).orElse(null);
            if (result != null && result.state() != SceneState.COMPLETED) {
                source.sendFailure(Component.literal(result.message()));
                return 0;
            }
            source.sendSuccess(() -> Component.literal("Started cutscene: " + id), false);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException error) {
            source.sendFailure(Component.literal(error.getMessage()));
            return 0;
        }
    }

    static ResourceLocation resolve(String name, List<ResourceLocation> ids) {
        if (name.indexOf(':') >= 0) {
            ResourceLocation id = ResourceLocation.tryParse(name);
            if (id != null && ids.contains(id)) {
                return id;
            }
        } else {
            List<ResourceLocation> matches = ids.stream().filter(id -> id.getPath().equals(name)).toList();
            if (matches.size() == 1) {
                return matches.getFirst();
            }
            if (matches.size() > 1) {
                throw new IllegalArgumentException("Several scenes have that name: " + matches
                        + ". Use the full ID in quotes.");
            }
        }
        throw new IllegalArgumentException("Cutscene not found: " + name);
    }

    private static Stream<ResourceLocation> sceneIds() {
        return Stream.concat(CinematicsAPI.registeredSceneIds().stream(),
                Stream.of(EPISODES).map(CutsceneDebug.Episode::id));
    }

    private int startDebug(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        UUID playerId = player.getUUID();
        if (!CinematicsAPI.supports(server, playerId, ClientFeature.CINEMATIC_CAMERA)) {
            source.sendFailure(Component.literal("The full showcase requires neoscutapi on the client."));
            return 0;
        }
        if (runs.getOrDefault(server, Map.of()).containsKey(playerId)
                || scenes.activeScenes(server).stream().anyMatch(scene -> scene.participants().stream()
                        .anyMatch(participant -> participant.playerId().equals(playerId)))) {
            source.sendFailure(Component.literal("A cutscene is already running for this player."));
            return 0;
        }
        if (!player.onGround() || player.isPassenger()) {
            source.sendFailure(Component.literal("Stand on level ground in an open area and try again."));
            return 0;
        }
        try {
            DebugRun run = new DebugRun();
            run.instanceId = startEpisode(server, player, EPISODES[0]);
            runs.computeIfAbsent(server, ignored -> new HashMap<>()).put(playerId, run);
            player.sendSystemMessage(Component.literal(
                    "The showcase has started. Stay nearby. Run /cutscene debugg stop to stop."));
            return 1;
        } catch (IllegalArgumentException | IllegalStateException error) {
            source.sendFailure(Component.literal(error.getMessage()));
            return 0;
        }
    }

    private int stopDebug(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        var serverRuns = runs.get(server);
        DebugRun run = serverRuns == null ? null : serverRuns.remove(player.getUUID());
        if (run == null) {
            source.sendFailure(Component.literal("No showcase is running."));
            return 0;
        }
        if (serverRuns.isEmpty()) {
            runs.remove(server);
        }
        if (run.instanceId != null) {
            scenes.stop(server, run.instanceId, "Showcase stopped.");
        }
        player.sendSystemMessage(Component.literal("Showcase stopped."));
        return 1;
    }

    private void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        var serverRuns = runs.get(server);
        if (serverRuns == null) {
            return;
        }
        for (var entry : new ArrayList<>(serverRuns.entrySet())) {
            UUID playerId = entry.getKey();
            DebugRun run = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null || player.hasDisconnected() || !advance(server, player, run)) {
                serverRuns.remove(playerId, run);
            }
        }
        if (serverRuns.isEmpty()) {
            runs.remove(server, serverRuns);
        }
    }

    private boolean advance(MinecraftServer server, ServerPlayer player, DebugRun run) {
        if (run.instanceId != null) {
            var result = scenes.result(server, run.instanceId).orElse(null);
            if (result == null) {
                if (scenes.find(server, run.instanceId).isPresent()) {
                    return true;
                }
                player.sendSystemMessage(Component.literal("Showcase stopped because the episode result is unavailable."));
                return false;
            }
            if (result.state() != SceneState.COMPLETED) {
                player.sendSystemMessage(Component.literal("Showcase stopped. " + result.message()));
                return false;
            }
            if (++run.nextEpisode == EPISODES.length) {
                player.sendSystemMessage(Component.literal("Showcase complete."));
                return false;
            }
            run.instanceId = null;
            run.pause = CutsceneDebug.PAUSE_TICKS;
            return true;
        }
        if (--run.pause > 0) {
            return true;
        }
        try {
            run.instanceId = startEpisode(server, player, EPISODES[run.nextEpisode]);
            return true;
        } catch (IllegalArgumentException | IllegalStateException error) {
            player.sendSystemMessage(Component.literal("Showcase stopped. " + error.getMessage()));
            return false;
        }
    }

    private UUID startEpisode(MinecraftServer server, ServerPlayer player, CutsceneDebug.Episode episode) {
        return scenes.start(server, CutsceneDebug.scene(episode, player.position(), player.getYRot(),
                player.level().dimension().location()), List.of(player.getUUID()));
    }

    private void onServerStopping(ServerStoppingEvent event) {
        runs.remove(event.getServer());
    }

    private static final class DebugRun {
        private UUID instanceId;
        private int nextEpisode;
        private int pause;
    }
}
