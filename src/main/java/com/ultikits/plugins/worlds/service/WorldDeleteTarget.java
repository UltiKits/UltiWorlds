package com.ultikits.plugins.worlds.service;

import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * The identity of the world a {@code /world delete} confirmation was asked about, as opposed to its
 * name (UltiKits/UltiWorlds#19). A confirmation can arrive long after the request -- the player's
 * window has no expiry, the console's repeat may come 30 seconds later -- and in between the named
 * world can be deleted and a different world created under the same name. Comparing a fresh capture
 * with the one taken at the request tells the two apart, so the replacement is never deleted on a
 * confirmation given for its predecessor.
 *
 * <p>Three observations, each {@code null} when it does not apply:
 * <ul>
 *   <li>the loaded world's {@link World#getUID()};</li>
 *   <li>the file-system identity ({@link BasicFileAttributes#fileKey()}) of the world container's
 *       entry for the name, read without following a symbolic link;</li>
 *   <li>the bytes of the folder's {@code uid.dat}, the identity the server writes into every world
 *       folder -- read only when the entry is a real directory, never through a link.</li>
 * </ul>
 * A recreated world gets a new {@code uid.dat} even when the file system reuses the folder's inode
 * number. None of the three changes while a world is simply played in: saving rewrites files inside
 * the folder, not the folder's own identity or {@code uid.dat}.
 *
 * @author wisdomme
 * @version 2.0.0
 */
public final class WorldDeleteTarget {

    private final UUID loadedUid;
    private final Object entryKey;
    private final byte[] uidDat;

    private WorldDeleteTarget(UUID loadedUid, Object entryKey, byte[] uidDat) {
        this.loadedUid = loadedUid;
        this.entryKey = entryKey;
        this.uidDat = uidDat;
    }

    /**
     * Observe the world currently known by {@code worldName}. Never throws: an entry that is absent
     * or unreadable is recorded as absent.
     */
    public static WorldDeleteTarget capture(String worldName) {
        World world = Bukkit.getWorld(worldName);
        UUID loadedUid = world == null ? null : world.getUID();

        Object entryKey = null;
        byte[] uidDat = null;
        File container = Bukkit.getWorldContainer();
        if (container != null) {
            Path entry = new File(container, worldName).toPath();
            try {
                BasicFileAttributes attributes =
                        Files.readAttributes(entry, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                entryKey = attributes.fileKey();
                if (attributes.isDirectory()) {
                    Path uidFile = entry.resolve("uid.dat");
                    if (Files.isRegularFile(uidFile, LinkOption.NOFOLLOW_LINKS)) {
                        uidDat = Files.readAllBytes(uidFile);
                    }
                }
            } catch (IOException e) {
                // Absent or unreadable: recorded as absent, which a present world never equals.
            }
        }
        return new WorldDeleteTarget(loadedUid, entryKey, uidDat);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WorldDeleteTarget)) {
            return false;
        }
        WorldDeleteTarget that = (WorldDeleteTarget) other;
        return Objects.equals(loadedUid, that.loadedUid)
                && Objects.equals(entryKey, that.entryKey)
                && Arrays.equals(uidDat, that.uidDat);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(loadedUid, entryKey) + Arrays.hashCode(uidDat);
    }
}
