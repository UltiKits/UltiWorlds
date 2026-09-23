package com.ultikits.plugins.worlds.commands;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Pending {@code /world delete} requests from the server console, which cannot use the player's
 * confirmation window (UltiKits/UltiWorlds#19). The console confirms a deletion by repeating the
 * same command, for the same world name, within {@link #WINDOW_MILLIS}.
 *
 * <p>A request is keyed by the sender's name and the world name exactly as typed, so a different
 * name, or the same name in other letter case, never confirms it. Each call to {@link #confirm}
 * removes the entry it finds, so a request confirms at most one deletion. Expired entries are
 * dropped on every call, which keeps the table bounded by the names typed in the last window.
 *
 * <p>The caller supplies the time. {@code WorldCommand} passes a monotonic clock, so in production the
 * time never steps backwards; tests inject their own and need no sleeping. A time earlier than the
 * request is still treated as "not confirmed" rather than as a very long window, as a guard for any
 * other caller.
 *
 * @author wisdomme
 * @version 2.0.0
 */
final class ConsoleDeleteConfirmations {

    /** How long a console request waits for its repeat. */
    static final long WINDOW_MILLIS = 30_000L;

    /** The same window in seconds, for the messages that tell the console about it. */
    static final long WINDOW_SECONDS = WINDOW_MILLIS / 1000L;

    private final Map<String, Long> pending = new HashMap<>();

    /**
     * Confirm a pending request, or record a new one.
     *
     * @param senderName the requesting sender's name
     * @param worldName  the world name exactly as typed
     * @param now        the current time in milliseconds
     * @return {@code true} if a request for this sender and name was recorded no more than
     *         {@link #WINDOW_MILLIS} before {@code now} -- it is consumed, and the caller may delete;
     *         {@code false} if there was none, or it had expired -- a new request is recorded at
     *         {@code now}, and the caller must delete nothing
     */
    synchronized boolean confirm(String senderName, String worldName, long now) {
        dropExpired(now);
        Long requestedAt = pending.remove(key(senderName, worldName));
        if (requestedAt != null && isInsideWindow(requestedAt, now)) {
            return true;
        }
        pending.put(key(senderName, worldName), now);
        return false;
    }

    /**
     * Forget any pending request for this sender and name. Called when a request or its repeat is
     * refused, so that a refusal never leaves a confirmation behind for a later repeat to use.
     */
    synchronized void discard(String senderName, String worldName) {
        pending.remove(key(senderName, worldName));
    }

    private void dropExpired(long now) {
        Iterator<Long> times = pending.values().iterator();
        while (times.hasNext()) {
            if (!isInsideWindow(times.next(), now)) {
                times.remove();
            }
        }
    }

    /** The one place the window is decided; a clock that stepped backwards is outside it. */
    private static boolean isInsideWindow(long requestedAt, long now) {
        return now >= requestedAt && now - requestedAt <= WINDOW_MILLIS;
    }

    private static String key(String senderName, String worldName) {
        return senderName + '\u0000' + worldName;
    }
}
