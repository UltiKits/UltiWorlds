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
 * <p>A request also records the identity of the world it was made about (a
 * {@link com.ultikits.plugins.worlds.service.WorldDeleteTarget}). A repeat whose world is no longer
 * that one -- deleted and recreated under the same name in between -- confirms nothing: it is
 * recorded as a new request for the world that is there now.
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

    /** What a call to {@link #confirm} did. */
    enum Outcome {
        /** A live request for the same world was consumed: the caller may delete. */
        CONFIRMED,
        /** No live request existed: one is recorded now, and the caller must delete nothing. */
        REQUESTED,
        /**
         * A live request existed, but for a different world under the same name: it is replaced by a
         * new request for the world there now, and the caller must delete nothing.
         */
        CHANGED
    }

    /** A pending request: when it was made, and what it was made about. */
    private static final class Request {
        private final long at;
        private final Object target;

        private Request(long at, Object target) {
            this.at = at;
            this.target = target;
        }
    }

    private final Map<String, Request> pending = new HashMap<>();

    /**
     * Confirm a pending request, or record a new one.
     *
     * @param senderName the requesting sender's name
     * @param worldName  the world name exactly as typed
     * @param target     the identity of the world known by that name now; compared with
     *                   {@code equals}
     * @param now        the current time in milliseconds
     * @return {@link Outcome#CONFIRMED} if a request for this sender, name and world was recorded no
     *         more than {@link #WINDOW_MILLIS} before {@code now} -- it is consumed; otherwise a new
     *         request is recorded at {@code now} and the result is {@link Outcome#CHANGED} when a
     *         live request for a different world was replaced, or {@link Outcome#REQUESTED}
     */
    synchronized Outcome confirm(String senderName, String worldName, Object target, long now) {
        dropExpired(now);
        Request request = pending.remove(key(senderName, worldName));
        boolean live = request != null && isInsideWindow(request.at, now);
        if (live && request.target.equals(target)) {
            return Outcome.CONFIRMED;
        }
        pending.put(key(senderName, worldName), new Request(now, target));
        return live ? Outcome.CHANGED : Outcome.REQUESTED;
    }

    /**
     * Forget any pending request for this sender and name. Called when a request or its repeat is
     * refused, so that a refusal never leaves a confirmation behind for a later repeat to use.
     */
    synchronized void discard(String senderName, String worldName) {
        pending.remove(key(senderName, worldName));
    }

    private void dropExpired(long now) {
        Iterator<Request> requests = pending.values().iterator();
        while (requests.hasNext()) {
            if (!isInsideWindow(requests.next().at, now)) {
                requests.remove();
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
