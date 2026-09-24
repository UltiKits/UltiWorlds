package com.ultikits.plugins.worlds.service;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * The one rule both {@code /world delete} confirmations follow (UltiKits/UltiWorlds#19): the
 * player's confirmation window and the console's typed repeat. Keeping it in one object is what
 * stops the two paths from drifting apart.
 *
 * <p><b>Time.</b> A confirmation is valid for {@link #WINDOW_MILLIS} after it was requested,
 * measured on a monotonic clock (changes to the system time neither lengthen nor shorten it). The
 * single predicate is {@link #isInside(long, long)}.
 *
 * <p><b>Deletions through this module.</b> {@link WorldService#deleteWorld(String)} records, before
 * it removes anything, that a world by that name is being deleted ({@link #invalidate(String)}). A
 * confirmation is void if such a record is at or after the time it was requested
 * ({@link #deletedSince(String, long)}): a world deleted through this module and created again under
 * the same name is never deleted on a confirmation given before that deletion -- also when the
 * deletion failed part-way, because the record is written first. A record matters only while a
 * confirmation older than it can still be inside the window, so records older than the window are
 * dropped whenever one is written, and uniquely named worlds do not accumulate.
 *
 * <p><b>What this deliberately does not do.</b> It does not try to recognise "the same world". A
 * world deleted by another plugin or by hand, and created again under the same name within the
 * window, is deleted by a confirmation given for its predecessor. That limit is bounded by the
 * window and is documented (maintainer decision of 2026-09-24, after an identity check was tried
 * and each refinement of it drew a new counter-case).
 *
 * <p>Names are compared without regard to letter case, as {@code CraftServer#getWorld} and this
 * module's protection check compare them; the error is on the side of voiding more confirmations.
 *
 * @author wisdomme
 * @version 2.0.0
 */
public final class DeleteConfirmationWindow {

    /** How long a delete confirmation stays valid. */
    public static final long WINDOW_MILLIS = 30_000L;

    /** The same window in seconds, for the messages that tell the sender about it. */
    public static final long WINDOW_SECONDS = WINDOW_MILLIS / 1000L;

    private final LongSupplier clock;
    /** Name (lower case) to the time of the latest deletion of that name, on this window's clock. */
    private final Map<String, Long> deletions = new ConcurrentHashMap<>();

    /** A window on the JVM's monotonic clock, in milliseconds. */
    public DeleteConfirmationWindow() {
        this(() -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
    }

    /** A window on the given clock, in milliseconds; for tests. */
    public DeleteConfirmationWindow(LongSupplier clock) {
        this.clock = clock;
    }

    /** The current time on this window's clock, in milliseconds. */
    public long now() {
        return clock.getAsLong();
    }

    /**
     * Whether a confirmation requested at {@code requestedAt} is still valid at {@code now}: no
     * earlier than the request and no more than {@link #WINDOW_MILLIS} after it, inclusive. The one
     * place the window is decided, for both the player and the console.
     */
    public boolean isInside(long requestedAt, long now) {
        return now >= requestedAt && now - requestedAt <= WINDOW_MILLIS;
    }

    /**
     * Whether this module has deleted, or started deleting, a world by this name at or after
     * {@code requestedAt}. At the same instant counts as after: the error is on the side of voiding.
     */
    public boolean deletedSince(String worldName, long requestedAt) {
        Long deletedAt = deletions.get(key(worldName));
        return deletedAt != null && deletedAt >= requestedAt;
    }

    /**
     * Record that this module is deleting a world by this name, voiding every confirmation
     * requested before now; and drop records too old to void a confirmation that is still valid.
     */
    public void invalidate(String worldName) {
        long now = now();
        deletions.values().removeIf(deletedAt -> !isInside(deletedAt, now));
        deletions.put(key(worldName), now);
    }

    /** How many names currently have a deletion record; for tests. */
    int recordedNames() {
        return deletions.size();
    }

    private static String key(String worldName) {
        return worldName.toLowerCase(Locale.ROOT);
    }
}
