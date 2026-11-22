package com.neocodder.darkmirror.cinematics.core;

import com.neocodder.darkmirror.cinematics.CinematicsAPI;
import com.neocodder.darkmirror.cinematics.DarkMirrorCinematics;
import com.neocodder.darkmirror.cinematics.api.ClientFeature;
import com.neocodder.darkmirror.cinematics.api.action.ActionExecution;
import com.neocodder.darkmirror.cinematics.api.action.ActionResult;
import com.neocodder.darkmirror.cinematics.api.action.ActorActions;
import com.neocodder.darkmirror.cinematics.api.action.BuiltInActions;
import com.neocodder.darkmirror.cinematics.api.action.SceneAction;
import com.neocodder.darkmirror.cinematics.api.actor.ActorAnimationAdapter;
import com.neocodder.darkmirror.cinematics.api.context.SceneContext;
import com.neocodder.darkmirror.cinematics.api.scene.CinematicScene;
import com.neocodder.darkmirror.cinematics.api.scene.SceneBuilder;
import com.neocodder.darkmirror.cinematics.api.scene.SceneType;
import com.neocodder.darkmirror.cinematics.network.CinematicsNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

final class CutsceneDebug {
    static final int EPISODE_TICKS = 400;
    static final int PAUSE_TICKS = 100;
    private static final String GUIDE = "guide";
    private static final String OTHER = "other";
    private static final ResourceLocation PIG = ResourceLocation.withDefaultNamespace("pig");
    private static final ResourceLocation SOUND = ResourceLocation.withDefaultNamespace("block.note_block.chime");
    private static final ResourceLocation PARTICLE = ResourceLocation.withDefaultNamespace("happy_villager");
    private static final ResourceLocation ANIMATION_ADAPTER = id("debug/vanilla");
    private static final ResourceLocation ANIMATION = id("debug/glow");
    private static final ResourceLocation CUSTOM_ACTION = id("debug/custom");

    private CutsceneDebug() {
    }

    static void registerDemoExtensions() {
        CinematicsAPI.actorAnimations().register(ANIMATION_ADAPTER, new ActorAnimationAdapter() {
            @Override
            public void play(Entity actor, ResourceLocation animationId) {
                actor.setGlowingTag(true);
            }

            @Override
            public void stop(Entity actor, ResourceLocation animationId) {
                actor.setGlowingTag(false);
            }
        });
        CinematicsAPI.customActions().register(CUSTOM_ACTION,
                parameters -> narration(parameters.get("line"), 160));
    }

    enum Episode {
        SPAWN("spawn", "Entity spawn"),
        MOB_AI("ai", "Mob AI and gravity"),
        TITLE("title", "Title and subtitle"),
        TALK("dialogue", "Character dialogue and narration"),
        SOUND("sound", "Participant sound"),
        PARTICLES("particles", "Particles"),
        FADE("fade", "Fade out and fade in"),
        ROUTE("route", "Actor movement and rotation"),
        APPROACH("approach", "Approaching a target"),
        JUMP("jump", "Scripted jump"),
        INTERACTION("actors", "Interaction between two actors"),
        ANIMATION("animation", "Animation adapter"),
        DESPAWN("despawn", "Early actor despawn"),
        REMOVE("remove", "Removing an entity by UUID"),
        TELEPORT("teleport", "Participant teleport"),
        CUSTOM("custom", "Custom action factory"),
        INTERACTIVE("interactive", "Following and answering the guide"),
        CAMERA_FOLLOW("camera_follow", "Cinematic camera following an actor", SceneType.CINEMATIC),
        CAMERA_PATH("camera_path", "Cinematic flythrough without actors", SceneType.CINEMATIC);

        private final ResourceLocation id;
        private final String label;
        private final SceneType type;

        Episode(String name, String label) {
            this(name, label, SceneType.INTERACTIVE);
        }

        Episode(String name, String label, SceneType type) {
            this.id = CutsceneDebug.id("debug/" + name);
            this.label = label;
            this.type = type;
        }

        ResourceLocation id() {
            return id;
        }

        SceneType type() {
            return type;
        }
    }

    static CinematicScene scene(Episode episode, Vec3 origin, float yaw, ResourceLocation dimension) {
        double angle = Math.toRadians(yaw);
        Vec3 forward = new Vec3(-Math.sin(angle), 0, Math.cos(angle));
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 start = origin.add(forward.scale(3));
        Vec3 next = origin.add(forward.scale(7));
        Vec3 side = start.add(right.scale(3));
        SceneBuilder scene = CinematicScene.builder(episode.id()).type(episode.type());
        if (episode == Episode.CAMERA_PATH) {
            return cameraPath(scene, origin, start, next, right);
        }

        if (episode != Episode.SPAWN) {
            spawn(scene, GUIDE, start, dimension);
        }
        scene.action(narration(episode.label + ".", 80));
        List<SceneAction> shots = new ArrayList<>();
        shots.add(BuiltInActions.waitTicks(EPISODE_TICKS));
        if (episode != Episode.TALK && episode != Episode.INTERACTIVE) {
            shots.add(at(85, ActorActions.say(GUIDE, guideLine("Watch what happens next."), 90)));
        }
        switch (episode) {
            case SPAWN -> {
                shots.add(at(30, ActorActions.spawn(GUIDE, PIG, dimension, start.x, start.y, start.z)));
                shots.add(at(31, ActorActions.lookAtPlayer(GUIDE)));
            }
            case MOB_AI -> {
                shots.add(at(45, nativeWalk(next)));
                shots.add(at(250, ActorActions.custom(GUIDE, (context, actor) -> {
                    if (actor instanceof Mob mob) {
                        mob.getNavigation().stop();
                        mob.setNoAi(true);
                    }
                })));
            }
            case TITLE -> shots.add(BuiltInActions.title(Component.literal("Demo scene"),
                    Component.literal("Subtitle and timing"), 15, 370, 15));
            case TALK -> {
                shots.add(at(85, ActorActions.say(GUIDE, guideLine("I am your guide."), 140)));
                shots.add(at(230, narration("The guide is waiting ahead.", 160)));
            }
            case SOUND -> {
                for (int tick = 20; tick < EPISODE_TICKS; tick += 75) {
                    shots.add(at(tick, BuiltInActions.playSound(SOUND, 0.75F, 1.0F + tick / 800.0F)));
                }
            }
            case PARTICLES -> {
                for (int tick = 5; tick < EPISODE_TICKS; tick += 20) {
                    shots.add(at(tick, BuiltInActions.spawnParticles(PARTICLE, dimension,
                            start.x, start.y + 1, start.z, 14, 0.3)));
                }
            }
            case FADE -> {
                shots.add(at(40, BuiltInActions.fadeOut(80)));
                shots.add(at(145, BuiltInActions.fadeIn(90)));
                shots.add(at(240, BuiltInActions.fadeOut(60)));
                shots.add(at(310, BuiltInActions.fadeIn(80)));
            }
            case ROUTE -> {
                shots.add(at(30, ActorActions.moveTo(GUIDE, next.x, next.y, next.z, 0.065)));
                shots.add(at(130, ActorActions.lookAt(GUIDE, side.x, side.y, side.z)));
                shots.add(at(170, ActorActions.moveTo(GUIDE, side.x, side.y, side.z, 0.065)));
                shots.add(at(290, ActorActions.lookAtPlayer(GUIDE)));
            }
            case APPROACH -> {
                spawn(scene, OTHER, next, dimension);
                shots.add(at(70, ActorActions.approach(GUIDE,
                        context -> context.actors().find(OTHER).orElse(null), 1.3, 0.045)));
            }
            case JUMP -> {
                shots.add(at(50, ActorActions.jump(GUIDE, 1.0, 45)));
                shots.add(at(175, ActorActions.jump(GUIDE, 1.6, 60)));
                shots.add(at(300, ActorActions.jump(GUIDE, 1.0, 55)));
            }
            case INTERACTION -> {
                spawn(scene, OTHER, side, dimension);
                scene.actor(GUIDE).interactWith(OTHER, (actor, other) -> {
                    actor.setGlowingTag(true);
                    other.setGlowingTag(true);
                }).endActor();
                shots.add(at(45, ActorActions.lookAtEntity(GUIDE,
                        context -> context.actors().find(OTHER).orElse(null))));
                shots.add(at(210, ActorActions.say(OTHER, Component.literal("Companion: I see your signal."), 150)));
            }
            case ANIMATION -> {
                shots.add(at(25, ActorActions.playAnimation(GUIDE, ANIMATION_ADAPTER, ANIMATION, 170)));
                shots.add(at(230, ActorActions.startAnimation(GUIDE, ANIMATION_ADAPTER, ANIMATION)));
                shots.add(at(345, ActorActions.stopAnimation(GUIDE, ANIMATION_ADAPTER, ANIMATION)));
            }
            case DESPAWN -> shots.add(at(210, ActorActions.despawn(GUIDE)));
            case REMOVE -> shots.add(at(210, contextAction(context -> {
                Entity actor = context.actors().find(GUIDE).orElseThrow();
                return BuiltInActions.removeEntity(dimension, actor.getUUID()).createExecution().tick(context);
            })));
            case TELEPORT -> shots.add(at(175, contextAction(context -> {
                Vec3 landing = origin.add(right.scale(2));
                if (context.onlinePlayers().stream().anyMatch(player ->
                        !player.level().dimension().location().equals(dimension)
                                || !player.serverLevel().loadedAndEntityCanStandOn(
                                        BlockPos.containing(landing.add(0, -0.02, 0)), player)
                                || !player.serverLevel().noCollision(player,
                                        player.getBoundingBox().move(landing.subtract(player.position()))))) {
                    return narration("The landing spot is blocked or has no solid ground.", 160)
                            .createExecution().tick(context);
                }
                return BuiltInActions.teleportParticipants(dimension, landing.x, landing.y, landing.z, yaw, 0)
                        .createExecution().tick(context);
            })));
            case CUSTOM -> shots.add(at(210, () -> CinematicsAPI.customActions().create(CUSTOM_ACTION,
                    java.util.Map.of("line", "A registered action factory created this line."))
                    .createExecution()));
            case INTERACTIVE -> {
                shots.add(at(85, ActorActions.say(GUIDE,
                        guideLine("Follow me. Crouch beside me to reply."), 145)));
                shots.add(at(95, ActorActions.moveTo(GUIDE, next.x, next.y, next.z, 0.045)));
                shots.add(at(198, listenForReply()));
            }
            case CAMERA_FOLLOW -> {
                shots.add(BuiltInActions.cameraFollowEntity(
                        context -> context.actors().find(GUIDE).orElse(null),
                        -forward.x * 3, 2.0, -forward.z * 3, EPISODE_TICKS));
                shots.add(at(25, ActorActions.moveTo(GUIDE, next.x, next.y, next.z, 0.022)));
                shots.add(at(240, ActorActions.say(GUIDE,
                        guideLine("The camera is following me."), 125)));
            }
            case CAMERA_PATH -> throw new IllegalStateException("The camera path is built separately");
        }
        return scene.parallel(shots.toArray(SceneAction[]::new)).build();
    }

    private static CinematicScene cameraPath(SceneBuilder scene, Vec3 origin, Vec3 start, Vec3 next, Vec3 right) {
        Vec3 high = next.add(right.scale(3)).add(0, 5, 0);
        Vec3 near = origin.subtract(right.scale(3)).add(0, 1.8, 0);
        Vec3 far = next.subtract(right.scale(4)).add(0, 7, 0);
        Vec3 finalView = origin.add(right.scale(2)).add(0, 3.0, 0);
        return scene.action(narration("The camera rises over the field before cutting to a closer view.", 110))
                .action(camera(high, start, 100))
                .dialogue(guideLine("Look down to the left. The path opens up below."), 100)
                .action(camera(near, next, 2))
                .waitTicks(40)
                .action(camera(far, start, 105))
                .action(camera(high, origin, 2))
                .waitTicks(35)
                .action(narration("The camera returns to the starting point.", 115))
                .action(camera(finalView, start, 90))
                .waitTicks(30)
                .build();
    }

    private static SceneAction camera(Vec3 position, Vec3 target, int ticks) {
        Vec3 delta = target.subtract(position);
        float yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90);
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance()));
        return BuiltInActions.cameraMove(position.x, position.y, position.z, yaw, pitch, ticks);
    }

    private static SceneAction listenForReply() {
        return () -> new ActionExecution() {
            private int remaining = EPISODE_TICKS - 198;

            @Override
            public ActionResult tick(SceneContext context) {
                Entity guide = context.actors().find(GUIDE).orElse(null);
                if (guide != null && context.onlinePlayers().stream().anyMatch(player ->
                        player.level() == guide.level() && player.distanceToSqr(guide) <= 9
                                && player.isShiftKeyDown())) {
                    return ActorActions.say(GUIDE, guideLine("I hear you. The path ahead is clear."), 100)
                            .createExecution().tick(context);
                }
                return --remaining == 0 ? ActionResult.completed() : ActionResult.running();
            }
        };
    }

    private static SceneAction nativeWalk(Vec3 target) {
        return ActorActions.custom(GUIDE, (context, actor) -> {
            actor.setNoGravity(false);
            if (actor instanceof Mob mob) {
                mob.setNoAi(false);
                mob.getNavigation().moveTo(target.x, target.y, target.z, 1.0);
            }
        });
    }

    private static void spawn(SceneBuilder scene, String actor, Vec3 position, ResourceLocation dimension) {
        scene.spawnActor(actor, PIG, dimension, position.x, position.y, position.z).endActor();
    }

    private static SceneAction narration(String line, int ticks) {
        Component text = Component.literal(line);
        return contextAction(context -> {
            for (var player : context.onlinePlayers()) {
                player.sendSystemMessage(text);
                if (CinematicsAPI.supports(context.server(), player.getUUID(), ClientFeature.DIALOGUE_OVERLAY)) {
                    CinematicsNetwork.sendDialogue(player, line, ticks);
                }
            }
            return ActionResult.completed();
        });
    }

    private static Component guideLine(String text) {
        return Component.literal("Guide: " + text);
    }

    private static SceneAction contextAction(Function<SceneContext, ActionResult> operation) {
        return () -> operation::apply;
    }

    private static SceneAction at(int delay, SceneAction action) {
        return new SceneAction() {
            @Override
            public ActionExecution createExecution() {
                return new ActionExecution() {
                    private int remaining = delay;
                    private ActionExecution execution;

                    @Override
                    public ActionResult tick(SceneContext context) {
                        if (remaining > 0) {
                            remaining--;
                            return ActionResult.running();
                        }
                        if (execution == null) {
                            execution = action.createExecution();
                        }
                        return execution.tick(context);
                    }

                    @Override
                    public void cancel(SceneContext context) {
                        if (execution != null) {
                            execution.cancel(context);
                        }
                    }
                };
            }

            @Override
            public boolean optional() {
                return action.optional();
            }

            @Override
            public Set<ClientFeature> requiredFeatures() {
                return action.requiredFeatures();
            }
        };
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(DarkMirrorCinematics.MOD_ID, path);
    }
}
