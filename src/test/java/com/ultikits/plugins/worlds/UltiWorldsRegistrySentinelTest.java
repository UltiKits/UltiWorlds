package com.ultikits.plugins.worlds;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Reopen guard: fails if this module ever loses its live test-time server bootstrap.
 * <p>
 * This sentinel deliberately routes through {@link UltiWorldsTestHelper#setUp()} /
 * {@link UltiWorldsTestHelper#tearDown()} -- the same shared bootstrap every other test class in
 * this module uses -- rather than calling {@code MockBukkit.mock()} itself. A sentinel that mocks
 * its own live server proves nothing about the module's shared wiring: it would stay green even if
 * someone silently deleted the {@code MockBukkitSupport.ensureCleanState()} / {@code
 * MockBukkit.mock()} call from {@link UltiWorldsTestHelper#setUp()} and re-mocked everything with
 * plain Mockito, because it would simply create its own unrelated live server.
 * <p>
 * Every assertion here is deliberately non-null on a live-server-backed accessor, never a bare
 * registry constant -- a bare constant (e.g. {@code Sound.AMBIENT_CAVE}) resolves via
 * {@link java.util.ServiceLoader} merely from the {@code mockbukkit-v1.21} dependency being on the
 * classpath, independent of the bootstrap below, and would stay green even if the bootstrap call
 * were silently deleted.
 */
public class UltiWorldsRegistrySentinelTest {

    @BeforeEach
    void setUp() throws Exception {
        UltiWorldsTestHelper.setUp();
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiWorldsTestHelper.tearDown();
    }

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
