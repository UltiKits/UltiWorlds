package com.ultikits.plugins.worlds.config;

import java.util.Arrays;
import java.util.List;

import com.ultikits.ultitools.abstracts.AbstractConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntry;
import com.ultikits.ultitools.annotations.config.NotEmpty;
import com.ultikits.ultitools.annotations.config.Range;

import lombok.Getter;
import lombok.Setter;

/**
 * World management configuration.
 *
 * @author wisdomme
 * @version 2.0.0
 */
@Getter
@Setter
@ConfigEntity("config/worlds.yml")
public class WorldConfig extends AbstractConfigEntity {

    /**
     * The longest {@code tp_to_world.cooldown} the setting accepts, in seconds. The teleport cooldown
     * table keeps an entry for this long, so a raised cooldown still counts it.
     */
    public static final int MAX_TP_COOLDOWN_SECONDS = 300;

    @NotEmpty
    @ConfigEntry(path = "default_world", comment = "Default world name")
    private String defaultWorld = "world";

    @ConfigEntry(path = "protected_worlds", comment = "Worlds that cannot be auto-unloaded or deleted")
    private List<String> protectedWorlds = Arrays.asList("world", "world_nether", "world_the_end");

    @ConfigEntry(path = "load_worlds_on_start", comment = "Worlds to load automatically on server start")
    private List<String> loadWorldsOnStart = Arrays.asList();

    // ==================== Auto-Unload Settings ====================

    @ConfigEntry(path = "auto_unload.enabled", comment = "Enable auto-unloading of empty worlds")
    private boolean autoUnloadEmptyWorlds = false;

    /**
     * Seconds between two auto-unload checks, and before the first one after load. Bound to
     * {@code WorldService#checkAutoUnloadEmptyWorlds} through the framework's config-bound
     * {@code @Scheduled}: 1 to 107374182, enforced by the framework's binding, not by {@code @Range}.
     * A module {@code @Range} on a bound field would make an out-of-range {@code /ul reload} throw
     * from the config reload and abort the rest of this module's reload, instead of keeping the
     * running interval. An invalid value refuses the module at load and is ignored, with a WARNING,
     * at {@code /ul reload}. At least 10 seconds is recommended; the old minimum of 10 is no longer
     * enforced.
     * <p>
     * No code in this module writes {@code worlds.yml}. The comment is a literal, which the framework
     * writes only when it inserts a missing key, so an existing file keeps the comment it has, byte
     * for byte. The old text is registered in {@code previousComments} so that, should this entry's
     * comment ever become a catalogue token, the old line is recognised as the framework's and the
     * operator's own comments stay untouched; for a literal comment the framework ignores the list.
     */
    @ConfigEntry(path = "auto_unload.check_interval",
            comment = "Check interval in seconds (1 to 107374182; at least 10 recommended)",
            previousComments = {"Check interval in seconds"})
    private int emptyWorldCheckInterval = 60;

    @Range(min = 60, max = 86400)
    @ConfigEntry(path = "auto_unload.unload_after", comment = "Unload world after being empty for this many seconds")
    private int emptyWorldUnloadAfter = 300;
    
    // ==================== Teleport Settings ====================

    @ConfigEntry(path = "tp_to_world.enabled", comment = "Allow players to teleport between worlds")
    private boolean tpToWorldEnabled = true;

    @ConfigEntry(path = "tp_to_world.permission_per_world", comment = "Require permission for each world")
    private boolean permissionPerWorld = false;

    @Range(min = 0, max = MAX_TP_COOLDOWN_SECONDS)
    @ConfigEntry(path = "tp_to_world.cooldown", comment = "World teleport cooldown in seconds")
    private int tpCooldown = 10;
    
    @ConfigEntry(path = "world_spawn.use_spawn_location", comment = "Teleport to world spawn instead of last location")
    private boolean useSpawnLocation = true;

    @ConfigEntry(path = "tp_to_world.show_description", comment = "Show world description to player on teleport")
    private boolean showDescriptionOnTeleport = true;

    // ==================== Inventory Isolation Settings ====================
    
    @ConfigEntry(path = "world_isolation.enabled", comment = "Enable per-world inventory isolation")
    private boolean inventoryIsolation = false;
    
    @ConfigEntry(path = "world_isolation.separate_inventory", comment = "Separate inventory per world")
    private boolean separateInventory = true;
    
    @ConfigEntry(path = "world_isolation.separate_ender_chest", comment = "Separate ender chest per world")
    private boolean separateEnderChest = true;
    
    @ConfigEntry(path = "world_isolation.separate_experience", comment = "Separate XP levels per world")
    private boolean separateExperience = false;
    
    @ConfigEntry(path = "world_isolation.separate_health", comment = "Separate health per world")
    private boolean separateHealth = false;
    
    @ConfigEntry(path = "world_isolation.separate_hunger", comment = "Separate hunger per world")
    private boolean separateHunger = false;
    
    @ConfigEntry(path = "world_isolation.separate_effects", comment = "Separate potion effects per world")
    private boolean separateEffects = false;
    
    @ConfigEntry(path = "world_isolation.shared_worlds", comment = "Worlds that share inventory (comma separated groups)")
    private List<String> sharedWorldGroups = Arrays.asList("world,world_nether,world_the_end");

    public WorldConfig(String configFilePath) {
        super(configFilePath);
    }
}
