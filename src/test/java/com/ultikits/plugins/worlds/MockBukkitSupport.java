package com.ultikits.plugins.worlds;

import java.lang.reflect.Field;

import org.bukkit.Bukkit;

import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Defensive MockBukkit singleton-cleanup helper.
 * <p>
 * MockBukkit keeps its server in two static fields — {@code Bukkit.server} and
 * {@code MockBukkit.mock} — that outlive any single test class, because Surefire reuses one JVM fork
 * across all of them. {@link MockBukkit#unmock()} normally clears both, but only if it runs to
 * completion: its own try/finally covers just the scheduler shutdown, so an exception thrown earlier
 * (while disabling plugins) propagates before the fields are nulled. A leftover non-null
 * {@code MockBukkit.mock} then makes the next {@link MockBukkit#mock()} call fail with
 * {@code "Already mocking"}, and the failure surfaces in whichever test class happens to run next
 * rather than in the one that caused it. The reflective fallback below closes that window.
 * <p>
 * This helper is deliberately local to this module rather than shared: a test-only utility published
 * from another repository would make this module's test suite depend on that repository's release
 * cadence, for roughly thirty lines of reflection.
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
            // MockBukkit 4.x holds the server in "private static ServerMock mock" — the legacy
            // be.seeseemelk.mockbukkit boolean "mocked" no longer exists.
            Field mockField = MockBukkit.class.getDeclaredField("mock");
            mockField.setAccessible(true);
            mockField.set(null, null);
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
