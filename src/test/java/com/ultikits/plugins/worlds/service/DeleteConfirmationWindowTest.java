package com.ultikits.plugins.worlds.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code UltiKits/UltiWorlds#19}: the one rule both {@code /world delete} confirmations follow --
 * the player's window and the console's typed repeat -- so that they cannot drift apart
 * (maintainer decision of 2026-09-24: bind a confirmation to time, not to the world's identity).
 *
 * @author wisdomme
 * @version 2.0.0
 */
@DisplayName("DeleteConfirmationWindow (UltiWorlds#19)")
class DeleteConfirmationWindowTest {

    @Test
    @DisplayName("the default clock is monotonic: it reads System.nanoTime in milliseconds, not the wall clock")
    void theDefaultClockIsMonotonic() {
        DeleteConfirmationWindow window = new DeleteConfirmationWindow();

        long before = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
        long value = window.now();
        long after = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());

        assertThat(value).isBetween(before, after);
    }

    @Test
    @DisplayName("WorldService's window uses that default clock")
    void theServiceWindowIsMonotonic() {
        DeleteConfirmationWindow window = new WorldService().getDeleteConfirmationWindow();

        long before = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
        long value = window.now();
        long after = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());

        assertThat(value).isBetween(before, after);
    }

    @Test
    @DisplayName("the window is 30 seconds, inclusive, and a time before the request is outside it")
    void theWindowBoundary() {
        DeleteConfirmationWindow window = new DeleteConfirmationWindow(new AtomicLong()::get);

        assertThat(window.isInside(1_000L, 1_000L)).isTrue();
        assertThat(window.isInside(1_000L, 31_000L)).isTrue();
        assertThat(window.isInside(1_000L, 31_001L)).isFalse();
        assertThat(window.isInside(1_000L, 999L)).isFalse();
        assertThat(DeleteConfirmationWindow.WINDOW_SECONDS).isEqualTo(30L);
    }

    @Test
    @DisplayName("a deletion voids confirmations for that name in any letter case, and no other name")
    void invalidationIsPerNameAndCaseInsensitive() {
        DeleteConfirmationWindow window = new DeleteConfirmationWindow(new AtomicLong()::get);
        long arena = window.deletions("arena");
        long lobby = window.deletions("lobby");

        window.invalidate("Arena");

        assertThat(window.deletions("arena")).isNotEqualTo(arena);
        assertThat(window.deletions("ARENA")).isEqualTo(window.deletions("arena"));
        assertThat(window.deletions("lobby")).isEqualTo(lobby);
    }
}
