package com.ultikits.plugins.worlds.commands;

import com.ultikits.plugins.worlds.service.DeleteConfirmationWindow;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Pending {@code /world delete} requests from the server console, which cannot use the player's
 * confirmation window (UltiKits/UltiWorlds#19). The console confirms a deletion by repeating the
 * same command, for the same world name, within {@link DeleteConfirmationWindow#WINDOW_MILLIS}.
 *
 * <p>A request is keyed by the sender's name and the world name exactly as typed, so a different
 * name, or the same name in other letter case, never confirms it. Each call to {@link #confirm}
 * removes the entry it finds, so a request confirms at most one deletion. Expired entries are
 * dropped on every call, which keeps the table bounded by the names typed in the last window.
 *
 * <p>Time and validity come from the {@link DeleteConfirmationWindow} the caller passes -- the same
 * object the player's confirmation window uses, so the two paths share one clock, one predicate and
 * one record of deletions. A request is void once this module has deleted a world by that name
 * after the request was made.
 *
 * @author wisdomme
 * @version 2.0.0
 */
final class ConsoleDeleteConfirmations {

    /** What a call to {@link #confirm} did. */
    enum Outcome {
        /** A live request was consumed: the caller may delete. */
        CONFIRMED,
        /** No live request existed: one is recorded now, and the caller must delete nothing. */
        REQUESTED,
        /**
         * A live request existed, but this module has deleted a world by that name since it was
         * made: it is replaced by a new request, and the caller must delete nothing.
         */
        INVALIDATED
    }

    /** A pending request: when it was made, and how many deletions of that name had happened. */
    private static final class Request {
        private final long at;
        private final long deletions;

        private Request(long at, long deletions) {
            this.at = at;
            this.deletions = deletions;
        }
    }

    private final Map<String, Request> pending = new HashMap<>();

    /**
     * Confirm a pending request, or record a new one.
     *
     * @param senderName the requesting sender's name
     * @param worldName  the world name exactly as typed
     * @param window     the shared confirmation window
     * @return {@link Outcome#CONFIRMED} if a request for this sender and name is still inside the
     *         window and no deletion of that name has happened since -- it is consumed; otherwise a
     *         new request is recorded now and the result is {@link Outcome#INVALIDATED} when a live
     *         request was voided by a deletion, or {@link Outcome#REQUESTED}
     */
    synchronized Outcome confirm(String senderName, String worldName, DeleteConfirmationWindow window) {
        long now = window.now();
        long deletions = window.deletions(worldName);
        dropExpired(window, now);
        Request request = pending.remove(key(senderName, worldName));
        boolean live = request != null && window.isInside(request.at, now);
        if (live && request.deletions == deletions) {
            return Outcome.CONFIRMED;
        }
        pending.put(key(senderName, worldName), new Request(now, deletions));
        return live ? Outcome.INVALIDATED : Outcome.REQUESTED;
    }

    /**
     * Forget any pending request for this sender and name. Called when a request or its repeat is
     * refused, so that a refusal never leaves a confirmation behind for a later repeat to use.
     */
    synchronized void discard(String senderName, String worldName) {
        pending.remove(key(senderName, worldName));
    }

    private void dropExpired(DeleteConfirmationWindow window, long now) {
        Iterator<Request> requests = pending.values().iterator();
        while (requests.hasNext()) {
            if (!window.isInside(requests.next().at, now)) {
                requests.remove();
            }
        }
    }

    private static String key(String senderName, String worldName) {
        return senderName + '\u0000' + worldName;
    }
}
