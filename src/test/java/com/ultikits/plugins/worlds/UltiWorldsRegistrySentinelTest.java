package com.ultikits.plugins.worlds;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Reopen guard: fails if this module ever loses its live test-time server bootstrap.
 * <p>
 * Every assertion here is deliberately non-null on a live-server-backed accessor, never a bare
 * registry constant — a bare constant (e.g. {@code Sound.AMBIENT_CAVE}) resolves via
 * {@link java.util.ServiceLoader} merely from the {@code mockbukkit-v1.21} dependency being on the
 * classpath, independent of whether {@code MockBukkit.mock()} ever ran, and would stay green even if
 * every bootstrap call were silently deleted.
 */
public class UltiWorldsRegistrySentinelTest {

    @Test
    void liveServerIsBootstrapped() {
        assertNotNull(Bukkit.getServer(), "live server bootstrap must be present");
    }

    @Test
    void unsafeValuesResolves() {
        assertNotNull(Bukkit.getUnsafe(), "UnsafeValues must resolve on a live server");
    }

    @Test
    void createProfileDoesNotSilentlyReturnNull() {
        Object profile = Bukkit.createProfile(UUID.randomUUID(), "SentinelPlayer");
        assertNotNull(profile, "createProfile must not silently return null");
    }

    @Test
    void itemStackConstructionResolvesRegistry() {
        ItemStack stack = new ItemStack(Material.DIAMOND);
        assertNotNull(stack);
        assertEquals(Material.DIAMOND, stack.getType());
    }
}
