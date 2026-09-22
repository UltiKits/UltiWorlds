package com.ultikits.plugins.worlds.service;

import com.ultikits.plugins.worlds.config.WorldConfig;
import com.ultikits.plugins.worlds.conversation.WorldCreateConversation;
import com.ultikits.plugins.worlds.entity.WorldSettings;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.PostConstruct;
import com.ultikits.ultitools.annotations.Scheduled;
import com.ultikits.ultitools.annotations.Service;
import com.ultikits.ultitools.interfaces.DataOperator;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.Difficulty;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for world management operations.
 *
 * @author wisdomme
 * @version 2.0.0
 */
@Service
public class WorldService {
    
    @Autowired
    private UltiToolsPlugin plugin;

    @Autowired
    private WorldConfig config;

    private DataOperator<WorldSettings> dataOperator;
    
    // Cache for world settings
    private final Map<String, WorldSettings> settingsCache = new ConcurrentHashMap<>();
    
    // Teleport cooldowns
    private final Map<UUID, Long> tpCooldowns = new ConcurrentHashMap<>();

    // Empty world timer (tracks how long a world has been empty)
    private final Map<String, Long> emptyWorldTimers = new ConcurrentHashMap<>();

    // Closes every line this service prints about a world's environment. It points at the
    // procedure instead of inlining one, and it does not vary by branch, so it cannot be true of
    // one folder shape and false of another -- see reportNoDecision for why that matters.
    private static final String WHERE_THE_PROCEDURE_LIVES =
        " What each of these folder shapes means, and what can be done about it, is in this"
            + " module's CHANGELOG.md changelog entry for this version, and in"
            + " UltiKits/UltiWorlds#22.";

    /**
     * Initialize the service with @PostConstruct.
     */
    @PostConstruct
    public void init() {
        this.dataOperator = plugin.getDataOperator(WorldSettings.class);

        // Load configured worlds on start
        for (String worldName : config.getLoadWorldsOnStart()) {
            loadWorld(worldName);
        }

        // Initialize settings for existing worlds
        for (World world : Bukkit.getWorlds()) {
            getOrCreateSettings(world.getName());
        }
    }

    /**
     * Auto-unload empty worlds periodically.
     * Scheduled task runs every 60 seconds (1200 ticks).
     */
    @Scheduled(period = 1200, async = false)
    public void checkAutoUnloadEmptyWorlds() {
        if (!config.isAutoUnloadEmptyWorlds()) {
            return;
        }

        long now = System.currentTimeMillis();
        int unloadAfter = config.getEmptyWorldUnloadAfter(); // in seconds

        for (World world : Bukkit.getWorlds()) {
            String worldName = world.getName();
            WorldSettings settings = getOrCreateSettings(worldName);

            // Skip if world should not auto-unload
            if (!settings.isAutoUnload() || isInProtectedWorlds(worldName)) {
                emptyWorldTimers.remove(worldName);
                continue;
            }

            if (world.getPlayers().isEmpty()) {
                // Start or check timer
                Long emptyStart = emptyWorldTimers.get(worldName);
                if (emptyStart == null) {
                    emptyWorldTimers.put(worldName, now);
                } else if (now - emptyStart > unloadAfter * 1000L) {
                    // World has been empty long enough, unload it
                    plugin.getLogger().info(
                        "Auto-unloading empty world: " + worldName
                    );
                    unloadWorld(worldName, true);
                    emptyWorldTimers.remove(worldName);
                }
            } else {
                // World has players, reset timer
                emptyWorldTimers.remove(worldName);
            }
        }
    }
    
    /**
     * Get or create world settings.
     */
    public WorldSettings getOrCreateSettings(String worldName) {
        if (settingsCache.containsKey(worldName)) {
            return settingsCache.get(worldName);
        }

        WorldSettings settings = dataOperator.query()
            .where("world_name").eq(worldName)
            .first();

        if (settings == null) {
            settings = WorldSettings.createDefault(worldName);
            dataOperator.insert(settings);
        }

        settingsCache.put(worldName, settings);

        // Apply difficulty if configured
        if (settings.getDifficulty() != null) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                try {
                    world.setDifficulty(Difficulty.valueOf(settings.getDifficulty()));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warn("Invalid difficulty for world " + worldName + ": " + settings.getDifficulty());
                }
            }
        }

        return settings;
    }
    
    /**
     * Update world settings.
     */
    public void updateSettings(WorldSettings settings) {
        try {
            dataOperator.update(settings);
        } catch (IllegalAccessException e) {
            plugin.getLogger().error("Failed to update world settings", e);
        }
        settingsCache.put(settings.getWorldName(), settings);
    }
    
    /**
     * Get all worlds.
     */
    public List<World> getAllWorlds() {
        return new ArrayList<>(Bukkit.getWorlds());
    }
    
    /**
     * Get all visible worlds.
     */
    public List<World> getVisibleWorlds() {
        List<World> visible = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            WorldSettings settings = getOrCreateSettings(world.getName());
            if (!settings.isHidden()) {
                visible.add(world);
            }
        }
        return visible;
    }
    
    /**
     * Check if player can enter world.
     */
    public boolean canEnterWorld(Player player, String worldName) {
        WorldSettings settings = getOrCreateSettings(worldName);
        
        // Check if blocked
        if (settings.isBlocked() && !player.hasPermission("ultiworlds.bypass.blocked")) {
            return false;
        }
        
        // Check if locked
        if (settings.isLocked() && !player.hasPermission("ultiworlds.bypass.locked")) {
            return false;
        }
        
        // Check permission
        if (config.isPermissionPerWorld() && 
            !player.hasPermission("ultiworlds.world." + worldName) &&
            !player.hasPermission("ultiworlds.world.*")) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Teleport player to world.
     */
    public boolean teleportToWorld(Player player, String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage(plugin.i18n("error.world_not_found")
                .replace("%world%", worldName));
            return false;
        }

        WorldSettings settings = getOrCreateSettings(worldName);

        if (!checkTeleportPermissions(player, worldName, settings)) {
            return false;
        }

        Location destination = resolveDestination(world, settings);
        player.teleport(destination);
        setTpCooldown(player.getUniqueId());

        String displayName = settings.getDisplayName() != null ? settings.getDisplayName() : worldName;
        player.sendMessage(plugin.i18n("success.teleported")
            .replace("%world%", displayName));

        sendDescription(player, displayName, settings);
        executePostTeleportCommands(player.getName(), worldName, settings);

        return true;
    }

    private boolean checkTeleportPermissions(Player player, String worldName, WorldSettings settings) {
        if (settings.isBlocked() && !player.hasPermission("ultiworlds.bypass.blocked")) {
            player.sendMessage(plugin.i18n("error.world_blocked"));
            return false;
        }
        if (settings.isLocked() && !player.hasPermission("ultiworlds.bypass.locked")) {
            player.sendMessage(plugin.i18n("error.world_locked"));
            return false;
        }
        if (config.isPermissionPerWorld()
                && !player.hasPermission("ultiworlds.world." + worldName)
                && !player.hasPermission("ultiworlds.world.*")) {
            player.sendMessage(plugin.i18n("error.no_permission"));
            return false;
        }
        if (!canTeleport(player.getUniqueId())) {
            int remaining = getRemainingCooldown(player.getUniqueId());
            player.sendMessage(plugin.i18n("error.cooldown")
                .replace("%time%", String.valueOf(remaining)));
            return false;
        }
        return true;
    }

    private Location resolveDestination(World world, WorldSettings settings) {
        if (config.isUseSpawnLocation() && settings.getSpawnX() != 0) {
            return new Location(world,
                settings.getSpawnX(), settings.getSpawnY(), settings.getSpawnZ(),
                settings.getSpawnYaw(), settings.getSpawnPitch());
        }
        return world.getSpawnLocation();
    }

    private void sendDescription(Player player, String displayName, WorldSettings settings) {
        if (!config.isShowDescriptionOnTeleport()) {
            return;
        }
        String description = settings.getDescription();
        if (description == null || description.isEmpty()) {
            return;
        }
        for (String line : description.split("\\n")) {
            String parsed = org.bukkit.ChatColor.translateAlternateColorCodes('&',
                line.replace("{player}", player.getName())
                    .replace("{world}", displayName));
            player.sendMessage(parsed);
        }
    }

    private void executePostTeleportCommands(String playerName, String worldName, WorldSettings settings) {
        String commands = settings.getPostTeleportCommands();
        if (commands == null || commands.isEmpty()) {
            return;
        }
        for (String cmd : commands.split("\\n")) {
            String parsed = cmd.trim()
                .replace("{player}", playerName)
                .replace("{world}", worldName);
            if (!parsed.isEmpty()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            }
        }
    }
    
    /**
     * Create a new world with all options.
     */
    public World createWorld(String name, World.Environment environment, WorldType type,
                             Boolean generateStructures, String seed) {
        if (!WorldCreateConversation.WORLD_NAME_PATTERN.matcher(name).matches()) {
            return null;
        }

        if (Bukkit.getWorld(name) != null) {
            return null;
        }
        
        WorldCreator creator = new WorldCreator(name);
        creator.environment(environment);
        creator.type(type);
        
        if (generateStructures != null) {
            creator.generateStructures(generateStructures);
        }
        
        if (seed != null && !seed.isEmpty()) {
            try {
                creator.seed(Long.parseLong(seed));
            } catch (NumberFormatException e) {
                // Use string hash as seed
                creator.seed(seed.hashCode());
            }
        }
        
        World world = creator.createWorld();
        if (world != null) {
            getOrCreateSettings(name);
        }
        return world;
    }
    
    /**
     * Create a new world.
     */
    public boolean createWorld(String name, World.Environment environment, WorldType type, String generator) {
        if (!WorldCreateConversation.WORLD_NAME_PATTERN.matcher(name).matches()) {
            return false;
        }

        if (Bukkit.getWorld(name) != null) {
            return false;
        }
        
        WorldCreator creator = new WorldCreator(name);
        creator.environment(environment);
        creator.type(type);
        
        if (generator != null && !generator.isEmpty()) {
            creator.generator(generator);
        }
        
        World world = creator.createWorld();
        if (world != null) {
            getOrCreateSettings(name);
            return true;
        }
        return false;
    }

    /**
     * Load an existing world, restoring the environment it was last known to have.
     *
     * <p>A bare {@link WorldCreator} defaults to {@link World.Environment#NORMAL}, so rebuilding an
     * unloaded world without saying which environment it belongs to silently turns a nether or an
     * end world into an overworld, over the same stored region files. The environment is read from
     * the world's own folder by {@link #inferEnvironmentFromWorldFolder(String, File)}, every time
     * this method is called: a world folder is mutable between two commands, so an answer derived
     * from it is only true of the moment it was derived.
     */
    public boolean loadWorld(String name) {
        if (!isFilesystemSafeWorldName(name)) {
            return false;
        }

        World loaded = Bukkit.getWorld(name);
        if (loaded != null) {
            return true; // Already loaded
        }

        File worldFolder = new File(Bukkit.getWorldContainer(), name);
        if (!worldFolder.exists()) {
            return false;
        }

        WorldCreator creator = new WorldCreator(name);
        World.Environment environment = inferEnvironmentFromWorldFolder(name, worldFolder);
        if (environment != null) {
            creator.environment(environment);
        }
        World world = creator.createWorld();

        if (world != null) {
            getOrCreateSettings(name);
            return true;
        }
        return false;
    }


    /**
     * Reads a world's environment off the dimension sub-folder the server writes inside its world
     * folder -- {@code DIM-1} for {@link World.Environment#NETHER}, {@code DIM1} for
     * {@link World.Environment#THE_END} -- and refuses to answer whenever that evidence is
     * ambiguous or untrustworthy, returning {@code null} instead.
     *
     * <p><b>Why it refuses rather than picking the likelier answer.</b> This runs unattended, at
     * every server start, for every world in {@code load_worlds_on_start} (see {@link #init()}). A
     * wrong answer here does not throw and does not corrupt anything -- it silently points the
     * server at a different set of region files, so everything players built in the other set stops
     * existing from their point of view. The population that installs this fix is, by definition,
     * servers that hit {@code UltiKits/UltiWorlds#22}: their nether world was reloaded as an
     * overworld, and overworld terrain was then generated into the world folder's <em>top-level</em>
     * {@code region} directory, beside the original nether data in {@code DIM-1}. A world folder
     * can therefore carry a dimension directory and a top-level {@code region} directory at the
     * same time, each holding a different world's terrain, and that shape is exactly what the
     * defect produced. The folder alone cannot say which of those two worlds the operator wants
     * back, so this method does not decide it.
     *
     * <p>The rules, in order:
     * <ol>
     *   <li>No {@code DIM-1} and no {@code DIM1}: no evidence, no inference, and no log. This is
     *       every ordinary overworld on every boot, and the outcome is identical to the previous
     *       behaviour, so a WARNING here would be noise that trains operators to ignore the ones
     *       that matter.</li>
     *   <li>A dimension entry that is a symbolic link: refuse. {@link #deleteFolder(File)} in this
     *       same class deliberately does not follow links, and a link can point anywhere, including
     *       outside the world -- so it is not evidence about this world.</li>
     *   <li>Both {@code DIM-1} and {@code DIM1}: refuse. That is the layout of a single-player save
     *       or a downloaded map, where all three dimensions share one folder, not of a server
     *       world.</li>
     *   <li>A dimension entry beside a top-level {@code region} directory: refuse, as above.</li>
     *   <li>Otherwise: answer, and say so at WARNING.</li>
     * </ol>
     *
     * <p>Only direct children are considered. A folder named like a dimension deeper inside the
     * world's own data (a datapack dimension, for instance) does not change the world's own
     * environment and must not be read as if it did.
     *
     * <p>Note the asymmetry rule 1 encodes: the absence of {@code region} is <em>not</em> taken as
     * evidence of a dimension. A world that has been created but has never saved a chunk has no
     * {@code region} directory either, so "no region, therefore nether" would be wrong.
     *
     * @return the inferred environment, or {@code null} when this method declines to infer one
     */
    private World.Environment inferEnvironmentFromWorldFolder(String name, File worldFolder) {
        boolean nether = isDirectChildDirectory(worldFolder, "DIM-1");
        boolean theEnd = isDirectChildDirectory(worldFolder, "DIM1");
        if (!nether && !theEnd) {
            // A link whose target is gone -- an unmounted volume, a moved directory -- reads as
            // "not a directory", so without this the folder is indistinguishable from an ordinary
            // overworld and the world is served as NORMAL in silence. That is the defect this
            // method exists to prevent, happening on the one input it could not see.
            String dangling = danglingDimensionLink(worldFolder);
            if (dangling != null) {
                reportNoDecision(name, "its '" + dangling + "' entry is a symbolic link that does"
                    + " not lead to a directory, so the folder cannot be read as the world it may"
                    + " belong to");
            }
            return null;
        }

        if (isSymbolicLink(worldFolder, "DIM-1") || isSymbolicLink(worldFolder, "DIM1")) {
            reportNoDecision(name, "its dimension entry is a symbolic link, and this module does"
                + " not follow links when reading a world folder, so whatever the link points at"
                + " was not read");
            return null;
        }
        if (nether && theEnd) {
            reportNoDecision(name, "its folder contains both a top-level 'DIM-1' directory and a"
                + " top-level 'DIM1' directory");
            return null;
        }
        String marker = nether ? "DIM-1" : "DIM1";
        if (isDirectChildDirectory(worldFolder, "region")) {
            reportNoDecision(name, "its folder contains a top-level '" + marker + "' directory"
                + " and a top-level 'region' directory, each holding a different world's terrain");
            return null;
        }

        World.Environment inferred = nether ? World.Environment.NETHER : World.Environment.THE_END;
        plugin.getLogger().warn(
            "World '" + name + "' was loaded as " + inferred
                + ", because its folder contains a top-level '" + marker + "' directory and no"
                + " top-level 'region' directory." + WHERE_THE_PROCEDURE_LIVES
        );
        return inferred;
    }

    /**
     * States at WARNING that no environment was applied to {@code name} and what was observed about
     * its folder, and points at where the procedure is written down. Announced once per world per
     * session -- a boot-time decision about which region files a world reads is not something an
     * operator should have to discover from missing buildings. (Once per session rather than once
     * per load: after this, {@code loadWorld} records what the server actually produced, so a
     * second load in the same session takes the recorded branch, which is silent because it is no
     * longer a guess.)
     *
     * <p><b>This reports an observation. It does not prescribe a remedy, and neither does the
     * answering path.</b> That is a deliberate change of shape, made after a remedy sentence here
     * was wrong twice in a row: first it told the operator to delete the other world's terrain, and
     * then, once corrected for the folder shape it was written for, it was wrong for the other two
     * branches -- on the symbolic-link branch there need not be an "other directory" at all, and
     * moving one out cannot reach an answer while the link is still a link. One sentence cannot
     * give a correct procedure for three structurally different situations, and each repair made it
     * right for one branch and left it wrong for the others.
     *
     * <p>The deeper reason is that the instruction contradicted the guard that prints it:
     * prescribing a remedy for a folder this method has just declared it cannot read is itself the
     * guess the method exists to refuse. A statement of what was observed cannot be wrong for a
     * different branch, because each branch states its own observation -- so the failure mode
     * shrinks from "an instruction that is wrong about a situation" to "a sentence that is wrong
     * about its own observation", which a test can pin, and each branch's observation is pinned by
     * one.
     *
     * @param name the world
     * @param observation what was found in the folder, stated as fact and owned by the caller
     */
    private void reportNoDecision(String name, String observation) {
        plugin.getLogger().warn(
            "World '" + name + "': no environment was applied, because " + observation + "."
                + " This module does not guess an environment it cannot read from the folder, so"
                + " the world was loaded with the server's own default environment -- the same as"
                + " before this version." + WHERE_THE_PROCEDURE_LIVES
        );
    }



    /** Whether {@code child} is a directory directly inside {@code parent}. */
    private static boolean isDirectChildDirectory(File parent, String child) {
        return new File(parent, child).isDirectory();
    }

    /**
     * The name of a dimension entry that is a symbolic link but does not lead to a directory, or
     * {@code null} if neither is. {@code DIM-1} is reported in preference to {@code DIM1} only so
     * that the observation names one entry rather than a list; either is enough to stop the folder
     * being read.
     */
    private static String danglingDimensionLink(File worldFolder) {
        if (isSymbolicLink(worldFolder, "DIM-1")) {
            return "DIM-1";
        }
        if (isSymbolicLink(worldFolder, "DIM1")) {
            return "DIM1";
        }
        return null;
    }

    /** Whether {@code child} inside {@code parent} exists and is a symbolic link. */
    private static boolean isSymbolicLink(File parent, String child) {
        return Files.isSymbolicLink(new File(parent, child).toPath());
    }

    
    /**
     * Unload a world.
     *
     */
    public boolean unloadWorld(String name, boolean save) {
        World world = Bukkit.getWorld(name);
        if (world == null) {
            return false;
        }

        // Move players to default world first
        World defaultWorld = Bukkit.getWorld(config.getDefaultWorld());
        if (defaultWorld == null) {
            defaultWorld = Bukkit.getWorlds().get(0);
        }
        
        for (Player player : world.getPlayers()) {
            player.teleport(defaultWorld.getSpawnLocation());
            player.sendMessage(plugin.i18n("success.world_unloading_tp"));
        }
        
        return Bukkit.unloadWorld(world, save);
    }
    
    /**
     * Delete a world (unload and delete files).
     *
     * <p>A world the operator marked as protected is refused here, in the service, rather than in
     * any one caller: {@link com.ultikits.plugins.worlds.gui.WorldDeleteConfirmPage} calls this
     * method directly, so a guard living only in {@code WorldCommand} would leave that path -- and
     * every future one -- able to delete a protected world. See {@link #isDeleteProtected(String)}.
     *
     * @return true only if a loaded world was unloaded and, when an on-disk folder existed, that
     *         folder was actually removed; false if the world is protected from deletion (in which
     *         case nothing at all is removed, not even the settings row), if there was nothing to
     *         delete, if unloading a loaded world failed (in which case nothing is removed from
     *         disk), or if the folder still exists after the deletion attempt (in which case the
     *         settings row is kept so the world can be retried or inspected).
     */
    public boolean deleteWorld(String name) {
        if (!isFilesystemSafeWorldName(name)) {
            return false;
        }

        if (isDeleteProtected(name)) {
            plugin.getLogger().warn(
                "Refused to delete world " + name + ": it is the configured default_world or is"
                    + " listed in protected_worlds."
            );
            return false;
        }

        World world = Bukkit.getWorld(name);
        boolean wasLoaded = world != null;
        if (wasLoaded) {
            if (!unloadWorld(name, false)) {
                return false;
            }
        }

        File worldFolder = new File(Bukkit.getWorldContainer(), name);
        boolean folderExisted = worldFolder.exists();
        if (folderExisted) {
            boolean isLink = Files.isSymbolicLink(worldFolder.toPath());
            if (isLink) {
                // Worth saying out loud: the operator asked to delete a world, and what this
                // command can reach is a link. The sentence describes the command's SCOPE rather
                // than reporting an outcome, because it is printed before the attempt -- a past
                // tense here is a claim about something that has not happened yet and may fail.
                plugin.getLogger().warn(
                    "World '" + name + "' is a symbolic link, not a world folder. Only the link"
                        + " entry is subject to this command; nothing it points at is read or"
                        + " deleted."
                );
            }
            boolean allEntriesDeleted = deleteFolder(worldFolder);
            if (!allEntriesDeleted || worldFolder.exists()) {
                // A link that could not be unlinked leaves a link, not files in a folder, and
                // saying "some files remain on disk" of a world whose data was never in this place
                // contradicts the line above it.
                plugin.getLogger().warn(
                    isLink
                        ? "Failed to remove the symbolic link for world " + name
                            + "; the link is still in the world container. Settings for this world"
                            + " were kept."
                        : "Failed to fully delete the folder for world " + name
                            + "; some files remain on disk. Settings for this world were kept."
                );
                return false;
            }
        }

        // Remove from database
        dataOperator.query()
            .where("world_name").eq(name)
            .delete();
        settingsCache.remove(name);

        return wasLoaded || folderExisted;
    }

    /**
     * Whether {@code name} names a world this module refuses to delete: the configured
     * {@code default_world}, or any entry of {@code protected_worlds}, whose own declared comment
     * reads "Worlds that cannot be auto-unloaded or deleted".
     *
     * <p>The comparison is case-insensitive, which is deliberately wider than the exact
     * {@code contains} check this list was originally read with. {@code CraftServer#getWorld} looks
     * its argument up as {@code name.toLowerCase(Locale.ROOT)}, so an exact-match guard here is
     * bypassable on every platform by typing a protected world's name in another case; on a
     * case-insensitive filesystem (Windows, macOS) {@code new File(worldContainer, name)} then
     * resolves to the real folder and the deletion goes through.
     *
     * <p>The wider match can err, and it errs in one direction only: it can refuse a world the
     * operator did not list. Two worlds that differ only by case cannot both be loaded, but two
     * such folders can both sit on disk on a case-sensitive filesystem, and
     * {@link #deleteWorld(String)} deliberately accepts an unloaded world -- so listing
     * {@code Arena} does now refuse {@code /world delete arena}. That is an inconvenience with a
     * manual workaround (rename, or drop the entry), whereas on the very same input the
     * exact-match version resolved {@code Bukkit.getWorld("arena")} to the loaded {@code Arena},
     * unloaded it, and deleted the {@code arena} folder. A refusal is the safe error here.
     *
     * <p>{@link String#equalsIgnoreCase(String)} is locale-independent, so this does not inherit
     * the Turkish dotted-I trap that a {@code toLowerCase()} without an explicit locale would.
     *
     * @param name the world name as typed by the caller
     * @return true if deleting this world must be refused
     */
    public boolean isDeleteProtected(String name) {
        if (name == null) {
            return false;
        }
        return name.equalsIgnoreCase(config.getDefaultWorld()) || isInProtectedWorlds(name);
    }

    /**
     * Case-insensitive membership test against {@code protected_worlds}. Shared by
     * {@link #isDeleteProtected(String)} and {@link #checkAutoUnloadEmptyWorlds()} so the two
     * halves of that key's declared promise cannot drift apart again.
     */
    private boolean isInProtectedWorlds(String name) {
        List<String> protectedWorlds = config.getProtectedWorlds();
        if (protectedWorlds == null) {
            return false;
        }
        for (String protectedWorld : protectedWorlds) {
            if (name.equalsIgnoreCase(protectedWorld)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code name} is safe to combine with {@link Bukkit#getWorldContainer()} to build a
     * {@link File} that always resolves to a direct child of that container.
     *
     * <p>This is deliberately narrower than {@link WorldCreateConversation#WORLD_NAME_PATTERN}: that
     * pattern is the creation wizard's own naming convention for <em>new</em> worlds, but
     * {@link #loadWorld}, {@link #deleteWorld}, and the per-world management commands operate on
     * worlds that may already exist -- created before the wizard shipped, or by other tooling -- so
     * they must not reject a name just because it does not follow the wizard's narrower
     * alphanumeric/length convention (for example, a name containing a dot). All this check rules
     * out is a directory separator or a parent-directory segment, either of which would let the
     * resulting {@code File} resolve to something other than a direct child of the world container.
     */
    public static boolean isFilesystemSafeWorldName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (name.contains("/") || name.contains("\\")) {
            return false;
        }
        return !".".equals(name) && !"..".equals(name);
    }

    /**
     * Delete folder recursively.
     *
     * <p>A linked directory is not part of the world folder, so only the link entry is removed --
     * this method never descends into a {@link Files#isSymbolicLink(java.nio.file.Path) symbolic
     * link}, even when it points at a directory. Only real subdirectories are recursed into.
     *
     * <p>That holds for {@code folder} itself as well as for anything under it. It did not, once:
     * the rule was written for a link found among the children, and a link in the root position
     * was followed, because {@link File#listFiles()} resolves it -- so everything under the target
     * was deleted through the link while only the link entry was reported as the world folder.
     * A rule about links has to hold wherever the link is, or it is a rule about one position.
     *
     * @return true if every entry under {@code folder} (and {@code folder} itself) was
     *         successfully removed; false if {@link File#delete()} refused any entry (for example
     *         a permission issue or a lingering lock), in which case some data may remain on disk.
     */
    private boolean deleteFolder(File folder) {
        if (Files.isSymbolicLink(folder.toPath())) {
            return folder.delete();
        }
        File[] files = folder.listFiles();
        boolean allDeleted = true;
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory() && !Files.isSymbolicLink(file.toPath())) {
                    if (!deleteFolder(file)) {
                        allDeleted = false;
                    }
                } else if (!file.delete()) {
                    allDeleted = false;
                }
            }
        }
        return folder.delete() && allDeleted;
    }
    
    /**
     * Set world spawn.
     */
    public void setWorldSpawn(String worldName, Location location) {
        WorldSettings settings = getOrCreateSettings(worldName);
        settings.setSpawnX(location.getX());
        settings.setSpawnY(location.getY());
        settings.setSpawnZ(location.getZ());
        settings.setSpawnYaw(location.getYaw());
        settings.setSpawnPitch(location.getPitch());
        updateSettings(settings);
        
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            world.setSpawnLocation(location);
        }
    }
    
    /**
     * Check teleport cooldown.
     */
    public boolean canTeleport(UUID playerUuid) {
        Long lastTp = tpCooldowns.get(playerUuid);
        if (lastTp == null) {
            return true;
        }
        return System.currentTimeMillis() - lastTp > config.getTpCooldown() * 1000L;
    }
    
    /**
     * Set teleport cooldown.
     */
    public void setTpCooldown(UUID playerUuid) {
        tpCooldowns.put(playerUuid, System.currentTimeMillis());
    }
    
    /**
     * Get remaining cooldown.
     */
    public int getRemainingCooldown(UUID playerUuid) {
        Long lastTp = tpCooldowns.get(playerUuid);
        if (lastTp == null) {
            return 0;
        }
        long remaining = (config.getTpCooldown() * 1000L) - (System.currentTimeMillis() - lastTp);
        return Math.max(0, (int) (remaining / 1000));
    }
    
    public WorldConfig getConfig() {
        return config;
    }
}
