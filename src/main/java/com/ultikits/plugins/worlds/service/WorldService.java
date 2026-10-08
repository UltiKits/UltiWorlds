package com.ultikits.plugins.worlds.service;

import com.ultikits.plugins.worlds.config.WorldConfig;
import com.ultikits.plugins.worlds.conversation.WorldCreateConversation;
import com.ultikits.plugins.worlds.entity.WorldSettings;
import com.ultikits.plugins.worlds.util.Placeholders;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.PostConstruct;
import com.ultikits.ultitools.annotations.Scheduled;
import com.ultikits.ultitools.annotations.Service;
import com.ultikits.ultitools.entities.WhereCondition;
import com.ultikits.ultitools.exceptions.DataAccessException;
import com.ultikits.ultitools.interfaces.DataOperator;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.Difficulty;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

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
    
    /**
     * Read cache of each world's settings row, keyed by the world's own name. Only ever read from, and
     * replaced by the row a change wrote ({@link #changeSettings}); nothing is ever written back from it
     * (UltiKits/UltiWorlds#55).
     */
    private final Map<String, WorldSettings> settingsCache = new ConcurrentHashMap<>();
    
    // Teleport cooldowns
    private final Map<UUID, Long> tpCooldowns = new ConcurrentHashMap<>();

    /**
     * The time source of the teleport cooldown, in milliseconds. A field so a test can hold time
     * still: a cooldown boundary read off the real clock depends on whether it ticks between two
     * calls (UltiKits/UltiWorlds#30).
     */
    private java.util.function.LongSupplier clock = System::currentTimeMillis;

    // Empty world timer (tracks how long a world has been empty)
    private final Map<String, Long> emptyWorldTimers = new ConcurrentHashMap<>();

    // The time limit and the deletion record both /world delete confirmations follow
    // (UltiKits/UltiWorlds#19); every deletion below voids earlier confirmations before it starts.
    private DeleteConfirmationWindow deleteConfirmationWindow = new DeleteConfirmationWindow();

    // Closes every line this service prints about a world's environment. It points at the
    // procedure instead of inlining one, and it does not vary by branch, so it cannot be true of
    // one folder shape and false of another -- see reportNoDecision for why that matters.
    /**
     * Initialize the service with @PostConstruct.
     */
    @PostConstruct
    public void init() {
        this.dataOperator = plugin.getDataOperator(WorldSettings.class);

        // Load configured worlds on start; an entry that cannot be loaded is named, not skipped
        // silently (maintainer decision 2026-09-27: refuse and name).
        for (String worldName : config.getLoadWorldsOnStart()) {
            if (!loadWorld(worldName)) {
                plugin.getLogger().warn(Placeholders.fill(plugin.i18n("log.load_on_start_failed"),
                    "{WORLD}", String.valueOf(worldName)));
            }
        }
        warnAboutUnknownDefaultWorld();

        // Initialize settings for existing worlds
        for (World world : Bukkit.getWorlds()) {
            getOrCreateSettings(world.getName());
        }
        warnAboutRowsUnderAnotherSpelling();
    }

    /**
     * Lists each stored settings row whose name is no loaded world's name but equals one ignoring
     * letter case, with one warning per row naming the row and the world. Before
     * UltiKits/UltiWorlds#46 a command typed in another case than the world's name stored its
     * result under the typed text. Settings are now always read and written under the server's own
     * spelling, so such a row is no longer used; it is listed, never merged or deleted (maintainer
     * decision of 2026-09-29).
     */
    private void warnAboutRowsUnderAnotherSpelling() {
        List<World> loaded = Bukkit.getWorlds();
        Set<String> exact = new HashSet<>();
        for (World world : loaded) {
            exact.add(world.getName());
        }
        for (WorldSettings row : dataOperator.getAll()) {
            String rowName = row.getWorldName();
            if (rowName == null || exact.contains(rowName)) {
                continue;
            }
            for (World world : loaded) {
                if (rowName.equalsIgnoreCase(world.getName())) {
                    plugin.getLogger().warn(Placeholders.fill(plugin.i18n("log.settings_row_case_mismatch"),
                        "{ROW}", rowName,
                        "{WORLD}", world.getName()));
                    break;
                }
            }
        }
    }

    /**
     * Names a {@code default_world} that is no loaded world, with the world players are sent to
     * instead: the server's first world. It was silent, so a typo quietly redirected every player
     * moved out of an unloaded or blocked world. Run at start and on every reload of the module.
     */
    public void warnAboutUnknownDefaultWorld() {
        String configured = config.getDefaultWorld();
        if (configured == null || Bukkit.getWorld(configured) != null || Bukkit.getWorlds().isEmpty()) {
            return;
        }
        plugin.getLogger().warn(Placeholders.fill(plugin.i18n("log.default_world_unknown"),
            "{VALUE}", configured,
            "{FALLBACK}", Bukkit.getWorlds().get(0).getName()));
    }

    /**
     * Auto-unload empty worlds: when {@code auto_unload.enabled} is true right now, unloads every
     * non-protected, {@code autoUnload}-eligible world that has been empty for
     * {@code auto_unload.unload_after} seconds; otherwise does nothing.
     * <p>
     * Timing is bound to {@code auto_unload.check_interval} (seconds) through the framework's
     * config-bound {@code @Scheduled}; the default lives only in {@link WorldConfig}. The same key is
     * the first delay, so the first check comes one full interval after the module loads. The value
     * must be 1 to 107374182 ({@code Integer.MAX_VALUE / 20}); at least 10 is recommended. An invalid
     * value refuses the module at load, and at {@code /ul reload} is not applied: the running interval
     * is kept, a WARNING names the key, and the reload is reported as partial. A changed valid value
     * applies at {@code /ul reload}, keeping the task's place in its cycle (the next check is the last
     * check plus the new interval, or the next tick if that moment has passed); a panel edit applies
     * at the next {@code /ul reload}. The binding is sync only, so the check runs on the main thread,
     * and requires {@code api-version: 630} in {@code plugin.yml}.
     * <p>
     * Before UltiKits/UltiWorlds#38 the key was read by nothing and the check ran every 60 seconds;
     * until this module declared {@code api-version: 630} a one-second counting tick applied the key
     * instead of the framework's binding.
     */
    @Scheduled(config = WorldConfig.class, periodKey = "auto_unload.check_interval", delayKey = "auto_unload.check_interval")
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
                        plugin.i18n("log.auto_unload").replace("{WORLD}", worldName)
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
                    plugin.getLogger().warn(Placeholders.fill(plugin.i18n("log.invalid_difficulty"),
                        "{WORLD}", worldName,
                        "{VALUE}", String.valueOf(settings.getDifficulty())));
                }
            }
        }

        return settings;
    }
    
    /**
     * At most this many read-and-write attempts for one settings change before it gives up as contended
     * (UltiKits/UltiWorlds#55).
     */
    static final int MAX_WRITE_ATTEMPTS = 3;

    /**
     * Change one world's stored settings so that the change can never revert a change another server
     * sharing the database made to the same row (UltiKits/UltiWorlds#55; maintainer decision of
     * 2026-10-06 00:04 -- the pattern UltiEconomy's balances and UltiTrade#54 follow).
     * <p>
     * The row is read from the database -- never from the read cache -- {@code change} is applied to it,
     * and it is written with {@code DataOperator#updateIf} conditioned on the values it was read with
     * ({@link #valuesAsRead}). {@code updateIf} writes every column, so the condition is what makes the
     * write change only what {@code change} changed: if another writer changed the row after the read,
     * nothing is written, the row is read again and {@code change} applied to the new values, at most
     * {@link #MAX_WRITE_ATTEMPTS} times. {@code change} therefore runs once per attempt, on a freshly
     * read row, and must set values from its own inputs or from the row it is given -- never from
     * another copy.
     * <p>
     * After a write the read cache holds the row as written, so this server's running checks also see
     * the other servers' changes that write was built on. The read cache is not otherwise refreshed from
     * the database: a change made on another server reaches this server's checks when this server next
     * changes any setting of that world, or after a restart.
     * <p>
     * Failures keep the behaviour of UltiKits/UltiWorlds#51: when no stored row exists any more (deleted
     * by another server or by hand), when every attempt lost to another writer, or when the entity's
     * fields cannot be read, {@code log.settings_update_failed} is logged, nothing is written, and the
     * change is kept in this server's memory only. Any other storage failure propagates, as before.
     *
     * @param worldName the world's own name ({@code World#getName()}), the key its settings are stored under
     * @param change    sets the fields this change is about on the row it is given
     * @return the settings this server now holds for the world
     */
    public WorldSettings changeSettings(String worldName, Consumer<WorldSettings> change) {
        // As every command did before it changed a setting: a world this server never read settings for
        // gets its row now.
        WorldSettings held = getOrCreateSettings(worldName);
        WorldSettings attempted = null;
        for (int attempt = 1; attempt <= MAX_WRITE_ATTEMPTS; attempt++) {
            WorldSettings row = readStoredRow(worldName);
            if (row == null) {
                change.accept(held);
                plugin.getLogger().error(plugin.i18n("log.settings_update_failed"));
                return held;
            }
            WhereCondition[] asRead = valuesAsRead(row);
            change.accept(row);
            try {
                if (dataOperator.updateIf(row, asRead)) {
                    settingsCache.put(worldName, row);
                    return row;
                }
            } catch (DataAccessException e) {
                // updateIf wraps the entity-field reflection failure as its cause; any other data
                // failure was never caught here and is not.
                if (!(e.getCause() instanceof IllegalAccessException)) {
                    throw e;
                }
                plugin.getLogger().error(plugin.i18n("log.settings_update_failed"), e.getCause());
                settingsCache.put(worldName, row);
                return row;
            }
            attempted = row;
        }
        plugin.getLogger().error(plugin.i18n("log.settings_update_failed"));
        settingsCache.put(worldName, attempted);
        return attempted;
    }

    /** The world's settings row as it is now in the database, or {@code null}. */
    private WorldSettings readStoredRow(String worldName) {
        return dataOperator.query()
            .where("world_name").eq(worldName)
            .first();
    }

    /**
     * One condition per stored column, holding the value {@code row} was read with -- the
     * compare-and-set of {@link #changeSettings}. The values are the field values themselves, bound
     * exactly as the framework binds them when it writes the row, so a column compares equal to the
     * value it was read as.
     * <p>
     * A column read as {@code null} ({@code difficulty} and {@code post_teleport_commands} of a world
     * that never had them set) is compared too, with a {@code null} value: {@code updateIf} reads a
     * {@code null} expected value as {@code IS NULL} on SQLite and MySQL, and as an absent or
     * {@code null} field on JSON. A value another server writes into such a column between this
     * change's read and its write therefore makes the write miss, and the change is read again and
     * re-applied on top of it, as for any other column (maintainer decision of 2026-10-06).
     * <p>
     * Two kinds of column are left out, each documented:
     * <ul>
     *   <li>{@code spawn_yaw} and {@code spawn_pitch}: declared {@code FLOAT}, which MySQL stores in
     *       single precision and compares with a bound value in double precision, so most values read
     *       back would never compare equal and every change would give up as contended. They are only
     *       ever written together with {@code spawn_x}, {@code spawn_y} and {@code spawn_z}, which are
     *       compared.</li>
     *   <li>{@code id}, which {@code updateIf} matches itself.</li>
     * </ul>
     */
    static WhereCondition[] valuesAsRead(WorldSettings row) {
        List<WhereCondition> conditions = new ArrayList<>();
        addCondition(conditions, "world_name", row.getWorldName());
        addCondition(conditions, "display_name", row.getDisplayName());
        addCondition(conditions, "description", row.getDescription());
        addCondition(conditions, "icon", row.getIcon());
        addCondition(conditions, "pvp_enabled", row.isPvpEnabled());
        addCondition(conditions, "monsters_enabled", row.isMonstersEnabled());
        addCondition(conditions, "animals_enabled", row.isAnimalsEnabled());
        addCondition(conditions, "weather_enabled", row.isWeatherEnabled());
        addCondition(conditions, "difficulty", row.getDifficulty());
        addCondition(conditions, "post_teleport_commands", row.getPostTeleportCommands());
        addCondition(conditions, "hidden", row.isHidden());
        addCondition(conditions, "locked", row.isLocked());
        addCondition(conditions, "blocked", row.isBlocked());
        addCondition(conditions, "auto_unload", row.isAutoUnload());
        addCondition(conditions, "protect_break", row.isProtectBreak());
        addCondition(conditions, "protect_place", row.isProtectPlace());
        addCondition(conditions, "protect_interact", row.isProtectInteract());
        addCondition(conditions, "protect_explosion", row.isProtectExplosion());
        addCondition(conditions, "spawn_x", row.getSpawnX());
        addCondition(conditions, "spawn_y", row.getSpawnY());
        addCondition(conditions, "spawn_z", row.getSpawnZ());
        addCondition(conditions, "created_at", row.getCreatedAt());
        return conditions.toArray(new WhereCondition[0]);
    }

    private static void addCondition(List<WhereCondition> conditions, String column, Object value) {
        conditions.add(WhereCondition.builder().column(column).value(value).build());
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
    public boolean teleportToWorld(Player player, String typedName) {
        World world = Bukkit.getWorld(typedName);
        if (world == null) {
            player.sendMessage(plugin.i18n("error.world_not_found")
                .replace("%world%", typedName));
            return false;
        }

        // The server's own spelling: Bukkit#getWorld ignores case, but settings are keyed by
        // World#getName(), so a name typed in another case must not look up (or create) a row
        // under the typed text, where a blocked or locked world would read as open.
        String worldName = world.getName();
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
        for (String line : WorldSettings.descriptionLines(settings.getDescription())) {
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
                reportNoDecision(name, plugin.i18n("log.environment.observation.dangling_link")
                    .replace("{ENTRY}", dangling));
            }
            return null;
        }

        if (isSymbolicLink(worldFolder, "DIM-1") || isSymbolicLink(worldFolder, "DIM1")) {
            reportNoDecision(name, plugin.i18n("log.environment.observation.dimension_link"));
            return null;
        }
        if (nether && theEnd) {
            reportNoDecision(name, plugin.i18n("log.environment.observation.both_dimensions"));
            return null;
        }
        String marker = nether ? "DIM-1" : "DIM1";
        if (isDirectChildDirectory(worldFolder, "region")) {
            reportNoDecision(name, plugin.i18n("log.environment.observation.dimension_and_region")
                .replace("{ENTRY}", marker));
            return null;
        }

        World.Environment inferred = nether ? World.Environment.NETHER : World.Environment.THE_END;
        plugin.getLogger().warn(Placeholders.fill(plugin.i18n("log.environment.inferred"),
            "{WORLD}", name,
            "{ENVIRONMENT}", String.valueOf(inferred),
            "{ENTRY}", marker));
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
     * @param observation what was found in the folder, stated as fact and owned by the caller,
     *                    already in the server's language
     */
    private void reportNoDecision(String name, String observation) {
        // Present tense throughout, and deliberately. This runs while the environment is being
        // decided -- before the server has been asked to build anything -- so a past tense here
        // states an outcome that has not happened and may not: `createWorld` can return null, and
        // the operator would then hold one line saying the world was loaded and another saying the
        // command failed. What is true at this moment is what this module supplies.
        plugin.getLogger().warn(Placeholders.fill(plugin.i18n("log.environment.none"),
            "{WORLD}", name,
            "{OBSERVATION}", observation));
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
            plugin.getLogger().warn(plugin.i18n("log.delete.refused_protected").replace("{WORLD}", name));
            return false;
        }

        // Before anything is removed: a confirmation given before this deletion must not delete
        // whatever is created under this name next, and that must hold even if a later step of
        // this deletion fails (UltiKits/UltiWorlds#19).
        deleteConfirmationWindow.invalidate(name);

        World world = Bukkit.getWorld(name);
        boolean wasLoaded = world != null;
        // The settings row and the world's folder both carry the server's own spelling of the name,
        // which may differ in case from the text this was called with: Bukkit#getWorld ignores
        // case, a settings row is keyed by World#getName(), and on a case-sensitive filesystem the
        // typed text names no folder at all (UltiKits/UltiWorlds#46, #52). A world that is not
        // loaded has only the text it was called with.
        String ownName = wasLoaded ? world.getName() : name;
        if (wasLoaded) {
            if (!unloadWorld(name, false)) {
                return false;
            }
        }

        File worldFolder = new File(Bukkit.getWorldContainer(), ownName);
        boolean isLink = Files.isSymbolicLink(worldFolder.toPath());
        // Ask what ENTRY is here, not what is at the other end of it. File#exists follows a link,
        // so for a link whose target is missing -- an unmounted volume, a moved directory -- it
        // answers about the target and reports "nothing here", leaving the entry in place. That is
        // the opposite of what deleting is for. Loading asks the other question and rightly keeps
        // File#exists: a link with no target has nothing to load.
        boolean folderExisted = Files.exists(worldFolder.toPath(), LinkOption.NOFOLLOW_LINKS);
        if (folderExisted) {
            if (isLink) {
                // Worth saying out loud: the operator asked to delete a world, and what this
                // command can reach is a link. The sentence describes the command's SCOPE rather
                // than reporting an outcome, because it is printed before the attempt -- a past
                // tense here is a claim about something that has not happened yet and may fail.
                plugin.getLogger().warn(plugin.i18n("log.delete.symbolic_link").replace("{WORLD}", name));
            }
            boolean allEntriesDeleted = deleteFolder(worldFolder);
            if (!allEntriesDeleted
                    || Files.exists(worldFolder.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                // A link that could not be unlinked leaves a link, not files in a folder, and
                // saying "some files remain on disk" of a world whose data was never in this place
                // contradicts the line above it.
                plugin.getLogger().warn((isLink
                        ? plugin.i18n("log.delete.link_remove_failed")
                        : plugin.i18n("log.delete.folder_remove_failed"))
                    .replace("{WORLD}", name));
                return false;
            }
        }

        // Remove from database
        dataOperator.query()
            .where("world_name").eq(ownName)
            .delete();
        settingsCache.remove(ownName);

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
     * Set world spawn. The five spawn columns are written as one change through {@link #changeSettings},
     * so they are never mixed with another server's spawn and no other setting is written back.
     */
    public void setWorldSpawn(String worldName, Location location) {
        changeSettings(worldName, settings -> {
            settings.setSpawnX(location.getX());
            settings.setSpawnY(location.getY());
            settings.setSpawnZ(location.getZ());
            settings.setSpawnYaw(location.getYaw());
            settings.setSpawnPitch(location.getPitch());
        });
        
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            world.setSpawnLocation(location);
        }
    }
    
    /**
     * Check teleport cooldown.
     */
    public boolean canTeleport(UUID playerUuid) {
        Long lastTp = liveCooldownEntry(playerUuid);
        if (lastTp == null) {
            return true;
        }
        return clock.getAsLong() - lastTp > config.getTpCooldown() * 1000L;
    }

    /**
     * A player's last teleport time, or {@code null}. An entry older than the longest cooldown the
     * setting accepts can no longer block anyone (strictly older: {@link #canTeleport} still blocks at
     * exactly the cooldown), so it is dropped here and whenever a teleport is
     * recorded: the table holds only players who teleported within that window. An entry past the
     * current cooldown but inside it is kept, so raising the cooldown and reloading still counts it.
     */
    private Long liveCooldownEntry(UUID playerUuid) {
        Long lastTp = tpCooldowns.get(playerUuid);
        if (lastTp != null && clock.getAsLong() - lastTp > KEEP_COOLDOWN_MS) {
            tpCooldowns.remove(playerUuid, lastTp);
            return null;
        }
        return lastTp;
    }

    private static final long KEEP_COOLDOWN_MS = WorldConfig.MAX_TP_COOLDOWN_SECONDS * 1000L;
    
    /**
     * Set teleport cooldown.
     */
    public void setTpCooldown(UUID playerUuid) {
        long now = clock.getAsLong();
        tpCooldowns.values().removeIf(time -> now - time > KEEP_COOLDOWN_MS);
        tpCooldowns.put(playerUuid, now);
    }
    
    /**
     * Get remaining cooldown.
     */
    public int getRemainingCooldown(UUID playerUuid) {
        Long lastTp = liveCooldownEntry(playerUuid);
        if (lastTp == null) {
            return 0;
        }
        long remaining = (config.getTpCooldown() * 1000L) - (clock.getAsLong() - lastTp);
        return Math.max(0, (int) (remaining / 1000));
    }
    
    public WorldConfig getConfig() {
        return config;
    }

    /** The time limit and deletion record both {@code /world delete} confirmations follow. */
    public DeleteConfirmationWindow getDeleteConfirmationWindow() {
        return deleteConfirmationWindow;
    }
}
