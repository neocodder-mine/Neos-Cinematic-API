package com.neocodder.darkmirror.cinematics.api.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.neocodder.darkmirror.cinematics.api.action.BuiltInActions;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class CustomActionRegistryTest {
    @Test
    void createsActionWithImmutableParameters() {
        CustomActionRegistry registry = new CustomActionRegistry();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("example", "custom");
        registry.register(id, parameters -> {
            assertThrows(UnsupportedOperationException.class, () -> parameters.put("extra", "value"));
            return BuiltInActions.finish();
        });

        assertTrue(registry.contains(id));
        registry.create(id, Map.of("name", "ritual"));
    }

    @Test
    void rejectsDuplicateFactoriesAndUnknownIds() {
        CustomActionRegistry registry = new CustomActionRegistry();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("example", "custom");
        registry.register(id, ignored -> BuiltInActions.finish());

        assertThrows(IllegalStateException.class, () -> registry.register(id, ignored -> BuiltInActions.finish()));
        assertThrows(IllegalArgumentException.class,
                () -> registry.create(ResourceLocation.fromNamespaceAndPath("example", "missing"), Map.of()));
    }

    @Test
    void copiesParametersBeforeCallingFactory() {
        CustomActionRegistry registry = new CustomActionRegistry();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("example", "custom");
        registry.register(id, parameters -> {
            assertEquals("line", parameters.get("dialogue"));
            return BuiltInActions.finish();
        });
        Map<String, String> mutable = new HashMap<>();
        mutable.put("dialogue", "line");

        registry.create(id, mutable);
        mutable.put("dialogue", "changed");

        assertEquals("changed", mutable.get("dialogue"));
    }
}
