package com.ultikits.plugins.worlds;

import com.destroystokyo.paper.MaterialTags;
import org.bukkit.Bukkit;
import org.bukkit.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Guard for UltiKits/UltiWorlds#77: a default-answer {@code mockStatic(Bukkit.class)} that is the
 * first code in the JVM to touch Bukkit's tag constants would initialise them to {@code null} for
 * every later test (the order-dependent CI failure fixed by UltiKits/UltiWorlds#74).
 * {@link com.ultikits.plugins.worlds.testutil.RegistryTagsWarmup} initialises those constants
 * against a live test server before the first test class, so this test passes in any order,
 * including when it runs alone.
 */
class DefaultBukkitStaticMockTagGuardTest {

    @Test
    void tagConstantsSurviveADefaultBukkitStaticMock() {
        try (MockedStatic<Bukkit> ignored = mockStatic(Bukkit.class)) {
            assertThat(Tag.ITEMS_ARROWS).isNotNull();
            assertThat(MaterialTags.ARROWS).isNotNull();
        }
    }
}
