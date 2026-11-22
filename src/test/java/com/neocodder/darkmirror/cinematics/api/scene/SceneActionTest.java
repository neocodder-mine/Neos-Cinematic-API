package com.neocodder.darkmirror.cinematics.api.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.neocodder.darkmirror.cinematics.api.action.ActionExecution;
import com.neocodder.darkmirror.cinematics.api.action.ActionResult;
import com.neocodder.darkmirror.cinematics.api.action.BuiltInActions;
import com.neocodder.darkmirror.cinematics.api.action.SceneAction;
import com.neocodder.darkmirror.cinematics.api.context.SceneContext;
import com.neocodder.darkmirror.cinematics.api.ClientFeature;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class SceneActionTest {
    @Test
    void sceneDefinitionIsImmutableAndRequiresAnAction() {
        List<SceneAction> actions = new ArrayList<>();
        actions.add(BuiltInActions.finish());
        CinematicScene scene = new CinematicScene(id("immutable"), actions);
        actions.clear();

        assertEquals(1, scene.actions().size());
        assertThrows(UnsupportedOperationException.class, () -> scene.actions().clear());
        assertThrows(IllegalArgumentException.class,
                () -> CinematicScene.builder(id("empty")).build());
    }

    @Test
    void waitCompletesAfterTheRequestedNumberOfTicks() {
        ActionExecution execution = BuiltInActions.waitTicks(3).createExecution();

        assertEquals(ActionResult.Status.RUNNING, execution.tick(null).status());
        assertEquals(ActionResult.Status.RUNNING, execution.tick(null).status());
        assertEquals(ActionResult.Status.COMPLETED, execution.tick(null).status());
    }

    @Test
    void parallelActionsProgressTogetherAndFinishWhenAllFinish() {
        int[] firstTicks = {0};
        int[] secondTicks = {0};
        SceneAction first = () -> context -> ++firstTicks[0] == 1
                ? ActionResult.completed() : ActionResult.failed("first ticked twice");
        SceneAction second = () -> context -> ++secondTicks[0] == 2
                ? ActionResult.completed() : ActionResult.running();
        ActionExecution execution = BuiltInActions.parallel(List.of(first, second)).createExecution();

        assertEquals(ActionResult.Status.RUNNING, execution.tick(null).status());
        assertEquals(1, firstTicks[0]);
        assertEquals(1, secondTicks[0]);
        assertEquals(ActionResult.Status.COMPLETED, execution.tick(null).status());
        assertEquals(1, firstTicks[0]);
        assertEquals(2, secondTicks[0]);
    }

    @Test
    void optionalParallelFailureCleansUpAndLetsSiblingsFinish() {
        List<String> calls = new ArrayList<>();
        SceneAction optional = new SceneAction() {
            @Override
            public ActionExecution createExecution() {
                return new ActionExecution() {
                    @Override
                    public ActionResult tick(SceneContext context) {
                        throw new IllegalStateException("optional failure");
                    }

                    @Override
                    public void cancel(SceneContext context) {
                        calls.add("optional cancelled");
                    }
                };
            }

            @Override
            public boolean optional() {
                return true;
            }
        };
        SceneAction sibling = () -> context -> {
            calls.add("sibling completed");
            return ActionResult.completed();
        };
        ActionExecution group = BuiltInActions.parallel(List.of(optional, sibling)).createExecution();

        assertEquals(ActionResult.Status.COMPLETED, group.tick(null).status());
        assertEquals(List.of("optional cancelled", "sibling completed"), calls);
    }

    @Test
    void parallelCleanupContinuesAfterAChildThrows() {
        List<String> cancelled = new ArrayList<>();
        SceneAction first = () -> runningWithCleanup(() -> {
            cancelled.add("first");
            throw new IllegalStateException("cleanup failure");
        });
        SceneAction second = () -> runningWithCleanup(() -> cancelled.add("second"));
        ActionExecution group = BuiltInActions.parallel(List.of(first, second)).createExecution();

        assertEquals(ActionResult.Status.RUNNING, group.tick(null).status());
        group.cancel(null);
        group.cancel(null);
        assertEquals(List.of("first", "second"), cancelled);
    }

    @Test
    void cancellationInsideAParallelChildPreventsLaterChildrenFromStarting() {
        ActionExecution[] group = new ActionExecution[1];
        SceneAction first = () -> context -> {
            group[0].cancel(context);
            return ActionResult.running();
        };
        SceneAction second = () -> {
            throw new AssertionError("A child started after cancellation");
        };
        group[0] = BuiltInActions.parallel(List.of(first, second)).createExecution();

        group[0].tick(null);
    }

    private static ActionExecution runningWithCleanup(Runnable cleanup) {
        return new ActionExecution() {
            @Override
            public ActionResult tick(SceneContext context) {
                return ActionResult.running();
            }

            @Override
            public void cancel(SceneContext context) {
                cleanup.run();
            }
        };
    }

    @Test
    void finishAndFailureOutcomesAreDistinct() {
        assertEquals(ActionResult.Status.FINISH_SCENE,
                BuiltInActions.finish().createExecution().tick(null).status());
        assertEquals("invalid target",
                ActionResult.failed("invalid target").message());
        assertThrows(IllegalArgumentException.class, () -> ActionResult.failed(" "));
    }

    @Test
    void actionCancellationIsDelegatedToTheExecution() {
        int[] cancellations = {0};
        SceneAction action = () -> new ActionExecution() {
            @Override
            public ActionResult tick(SceneContext context) {
                return ActionResult.running();
            }

            @Override
            public void cancel(SceneContext context) {
                cancellations[0]++;
            }
        };
        ActionExecution execution = action.createExecution();

        execution.tick(null);
        execution.cancel(null);

        assertEquals(1, cancellations[0]);
    }

    @Test
    void concurrentDefinitionsCreateIndependentWaitState() {
        SceneAction wait = BuiltInActions.waitTicks(2);
        ActionExecution first = wait.createExecution();
        ActionExecution second = wait.createExecution();

        assertEquals(ActionResult.Status.RUNNING, first.tick(null).status());
        assertEquals(ActionResult.Status.RUNNING, second.tick(null).status());
        assertEquals(ActionResult.Status.COMPLETED, first.tick(null).status());
        assertEquals(ActionResult.Status.COMPLETED, second.tick(null).status());
    }

    @Test
    void builderPreservesOrder() {
        List<String> order = new ArrayList<>();
        CinematicScene scene = CinematicScene.builder(id("order"))
                .serverAction(context -> order.add("first"))
                .serverAction(context -> order.add("second"))
                .build();

        scene.actions().forEach(action -> action.createExecution().tick(null));

        assertEquals(List.of("first", "second"), order);
        assertTrue(scene.actions().size() == 2);
    }

    @Test
    void scenesDefaultToInteractiveAndCameraActionsRequireCinematicMode() {
        CinematicScene interactive = CinematicScene.builder(id("interactive"))
                .waitTicks(1)
                .build();
        CinematicScene cinematic = CinematicScene.builder(id("cinematic"))
                .type(SceneType.CINEMATIC)
                .cameraMove(1.0, 80.0, 1.0, 0.0F, 0.0F, 20)
                .build();

        assertEquals(SceneType.INTERACTIVE, interactive.type());
        assertEquals(SceneType.CINEMATIC, cinematic.type());
        assertTrue(cinematic.actions().get(0).requiredFeatures()
                .contains(ClientFeature.CINEMATIC_CAMERA));
        assertThrows(IllegalStateException.class, () -> CinematicScene.builder(id("invalid_camera"))
                .cameraMove(1.0, 80.0, 1.0, 0.0F, 0.0F, 20)
                .build());
    }

    @Test
    void actorBuilderAddsSpawnMovementAnimationDialogueAndDespawnInOrder() {
        CinematicScene scene = CinematicScene.builder(id("actor_script"))
                .spawnActor("stranger", id("scripted_stranger"), id("overworld"), 1.0, 64.0, 1.0)
                .moveTo(5.0, 64.0, 1.0, 0.1)
                .playAnimation(id("animations"), id("walk"), 20)
                .say(net.minecraft.network.chat.Component.literal("Wait."), 10)
                .despawn()
                .endActor()
                .finish()
                .build();

        assertEquals(6, scene.actions().size());
        assertThrows(IllegalArgumentException.class, () -> CinematicScene.builder(id("invalid_actor"))
                .spawnActor(" ", id("scripted_stranger"), id("overworld"), 0.0, 64.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> CinematicScene.builder(id("invalid_speed"))
                .actor("stranger")
                .moveTo(1.0, 64.0, 1.0, 0.0));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }
}
