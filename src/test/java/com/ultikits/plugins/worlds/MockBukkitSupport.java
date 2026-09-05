package com.ultikits.plugins.worlds;

import java.lang.reflect.Field;

import org.bukkit.Bukkit;

import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Defensive MockBukkit singleton-cleanup helper.
 * <p>
 * Copied (logic only, per phase-14's "no shared artifact" decision) from
 * {@code com.ultikits.ultitools.utils.MockBukkitHelper} in the framework repository — never add a
 * dependency on that class. Reconciles the fact that {@link MockBukkit#unmock()} alone can leave a
 * stale {@code Bukkit.server}/{@code MockBukkit.mocked} singleton behind between test classes reused
 * in the same Surefire fork.
 */
@SuppressWarnings("PMD.AvoidAccessibilityAlteration") // Test helper requires reflection for singleton cleanup
public final class MockBukkitSupport {

    private MockBukkitSupport() {
        // utility class, no instances
    }

    /**
     * Defensively clear the MockBukkit and Bukkit singleton state.
     * Call at the start of every test's {@code @BeforeEach}, before {@link MockBukkit#mock()}.
     */
    public static void ensureCleanState() {
        try {
            if (MockBukkit.isMocked()) {
                MockBukkit.unmock();
            }
        } catch (Exception ignored) {
            // best-effort cleanup only
        }

        try {
            Field mockedField = MockBukkit.class.getDeclaredField("mocked");
            mockedField.setAccessible(true);
            mockedField.setBoolean(null, false);
        } catch (Exception ignored) {
            // best-effort cleanup only
        }

        if (Bukkit.getServer() != null) {
            try {
                Field serverField = Bukkit.class.getDeclaredField("server");
                serverField.setAccessible(true);
                serverField.set(null, null);
            } catch (Exception ignored) {
                // best-effort cleanup only
            }
        }
    }

    /**
     * Safely unmock, then run {@link #ensureCleanState()} regardless of outcome.
     * Call at the end of every test's {@code @AfterEach}.
     */
    public static void safeUnmock() {
        try {
            MockBukkit.unmock();
        } catch (Exception ignored) {
            // best-effort cleanup only
        }
        ensureCleanState();
    }
}
