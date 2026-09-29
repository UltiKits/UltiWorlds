package com.ultikits.plugins.worlds.listener;

import com.ultikits.plugins.worlds.entity.WorldSettings;
import com.ultikits.plugins.worlds.service.InventoryIsolationService;
import com.ultikits.plugins.worlds.service.WorldService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.EventListener;
import com.ultikits.ultitools.annotations.PostConstruct;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Listener for world events including protection.
 *
 * @author wisdomme
 * @version 2.0.0
 */
@EventListener
public class WorldListener implements Listener {

    @Autowired
    private UltiToolsPlugin plugin;

    @Autowired
    private WorldService worldService;

    @Autowired(required = false)
    private InventoryIsolationService inventoryService;
    
    // ==================== Protection Events ====================
    
    /**
     * Handle block break protection.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        World world = event.getBlock().getWorld();
        WorldSettings settings = worldService.getOrCreateSettings(world.getName());
        
        if (settings.isProtectBreak() && !player.hasPermission("ultiworlds.bypass.protection")) {
            event.setCancelled(true);
            player.sendMessage(plugin.i18n("protection.break_denied"));
        }
    }
    
    /**
     * Handle block place protection.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        World world = event.getBlock().getWorld();
        WorldSettings settings = worldService.getOrCreateSettings(world.getName());
        
        if (settings.isProtectPlace() && !player.hasPermission("ultiworlds.bypass.protection")) {
            event.setCancelled(true);
            player.sendMessage(plugin.i18n("protection.place_denied"));
        }
    }
    
    /**
     * Handle interaction protection.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getClickedBlock() == null) return;
        
        World world = event.getClickedBlock().getWorld();
        WorldSettings settings = worldService.getOrCreateSettings(world.getName());
        
        if (settings.isProtectInteract() && !player.hasPermission("ultiworlds.bypass.protection")) {
            event.setCancelled(true);
            player.sendMessage(plugin.i18n("protection.interact_denied"));
        }
    }
    
    /**
     * Handle explosion protection.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        World world = event.getLocation().getWorld();
        if (world == null) return;
        
        WorldSettings settings = worldService.getOrCreateSettings(world.getName());
        
        if (settings.isProtectExplosion()) {
            event.blockList().clear();
        }
    }
    
    /**
     * Handle PVP toggle: a player's hit on a player in a world with PvP off does no damage. The
     * attacker is told by {@link #onPlayerAttackAttempt}, not here: with the world's own PvP rule
     * off (applied on entering it), the server stops the hit before this event is handled
     * (UltiKits/UltiWorlds#23), so a message sent here never arrived.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!(event.getDamager() instanceof Player)) return;
        
        World world = event.getEntity().getWorld();
        WorldSettings settings = worldService.getOrCreateSettings(world.getName());
        
        if (!settings.isPvpEnabled()) {
            event.setCancelled(true);
        }
    }

    /** Paper's attack-attempt event; present on Paper builds from November 2022 on (1.19.3 and later). */
    static final String ATTACK_ATTEMPT_EVENT = "io.papermc.paper.event.player.PrePlayerAttackEntityEvent";

    // The event's two accessors, looked up on the first attempt (the event class is not named here).
    private volatile Method attemptAttacked;
    private volatile Method attemptWillAttack;

    /**
     * Registers {@link #onPlayerAttackAttempt} for Paper's attack-attempt event, when the server has
     * it. The event is looked up by name and no method of this class names it: resolving a class's
     * methods resolves their parameter types, so naming a class the server lacks would stop this whole
     * listener from loading on an older server. There, nothing is registered and only the
     * PvP-disabled line is missing. The registration belongs to this listener, so the framework's
     * unregistration of this listener on module unload removes it too.
     */
    @PostConstruct
    public void registerAttackAttemptHandler() {
        Class<? extends Event> type = eventType(ATTACK_ATTEMPT_EVENT);
        Plugin host = Bukkit.getPluginManager().getPlugin("UltiTools");
        if (type == null || host == null) {
            return;
        }
        Bukkit.getPluginManager().registerEvent(type, this, EventPriority.MONITOR,
            (listener, event) -> onPlayerAttackAttempt(event), host, true);
    }

    /** The event class named {@code className}, or {@code null} when this server does not have it. */
    static Class<? extends Event> eventType(String className) {
        try {
            return Class.forName(className).asSubclass(Event.class);
        } catch (ClassNotFoundException | ClassCastException | LinkageError e) {
            return null;
        }
    }

    /**
     * Tells a player who swings at another player in a world with PvP off why the hit does
     * nothing. The attack attempt is announced before any damage logic, whatever stops the hit
     * afterwards, so the line reaches the attacker whenever the protection engages
     * (UltiKits/UltiWorlds#23).
     */
    void onPlayerAttackAttempt(Event event) {
        if (!(event instanceof PlayerEvent)) return;
        Entity attacked;
        boolean willAttack;
        try {
            if (attemptAttacked == null) {
                // willAttack first: a non-null attemptAttacked then implies both are set.
                attemptWillAttack = event.getClass().getMethod("willAttack");
                attemptAttacked = event.getClass().getMethod("getAttacked");
            }
            attacked = (Entity) attemptAttacked.invoke(event);
            willAttack = (Boolean) attemptWillAttack.invoke(event);
        } catch (ReflectiveOperationException | ClassCastException e) {
            return;
        }
        if (!willAttack || !(attacked instanceof Player)) return;

        World world = attacked.getWorld();
        WorldSettings settings = worldService.getOrCreateSettings(world.getName());

        if (!settings.isPvpEnabled()) {
            ((PlayerEvent) event).getPlayer().sendMessage(plugin.i18n("protection.pvp_disabled"));
        }
    }
    
    // ==================== Access Control ====================
    
    /**
     * Block teleport to blocked/locked worlds.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        World toWorld = event.getTo().getWorld();
        if (toWorld == null) return;
        
        // Skip if same world
        if (event.getFrom().getWorld() == toWorld) return;
        
        Player player = event.getPlayer();
        if (!worldService.canEnterWorld(player, toWorld.getName())) {
            event.setCancelled(true);
            
            WorldSettings settings = worldService.getOrCreateSettings(toWorld.getName());
            if (settings.isBlocked()) {
                player.sendMessage(plugin.i18n("error.world_blocked"));
            } else if (settings.isLocked()) {
                player.sendMessage(plugin.i18n("error.world_locked"));
            } else {
                player.sendMessage(plugin.i18n("error.no_permission"));
            }
        }
    }
    
    // ==================== World Rule Events ====================
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        World world = event.getLocation().getWorld();
        if (world == null) return;
        
        WorldSettings settings = worldService.getOrCreateSettings(world.getName());
        
        switch (event.getEntityType()) {
            case ZOMBIE:
            case SKELETON:
            case SPIDER:
            case CREEPER:
            case ENDERMAN:
            case WITCH:
            case SLIME:
            case PHANTOM:
            case DROWNED:
            case BLAZE:
            case GHAST:
            case WITHER_SKELETON:
            case PIGLIN:
            case PIGLIN_BRUTE:
            case HOGLIN:
            case ZOGLIN:
            case WARDEN:
                if (!settings.isMonstersEnabled()) {
                    event.setCancelled(true);
                }
                break;
            case COW:
            case PIG:
            case SHEEP:
            case CHICKEN:
            case HORSE:
            case RABBIT:
            case WOLF:
            case CAT:
            case FOX:
            case BEE:
            case GOAT:
            case FROG:
            case AXOLOTL:
            case CAMEL:
            case SNIFFER:
                if (!settings.isAnimalsEnabled()) {
                    event.setCancelled(true);
                }
                break;
            default:
                break;
        }
    }
    
    @EventHandler
    public void onWeatherChange(WeatherChangeEvent event) {
        World world = event.getWorld();
        WorldSettings settings = worldService.getOrCreateSettings(world.getName());
        
        if (!settings.isWeatherEnabled() && event.toWeatherState()) {
            event.setCancelled(true);
        }
    }

    /**
     * Rain and thunder are two independent states, so disabled weather also keeps thunder from
     * starting; thunder stopping is always allowed (UltiKits/UltiWorlds#24).
     */
    @EventHandler
    public void onThunderChange(ThunderChangeEvent event) {
        WorldSettings settings = worldService.getOrCreateSettings(event.getWorld().getName());

        if (!settings.isWeatherEnabled() && event.toThunderState()) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        // Apply world PVP settings
        Player player = event.getPlayer();
        World world = player.getWorld();
        WorldSettings settings = worldService.getOrCreateSettings(world.getName());
        
        world.setPVP(settings.isPvpEnabled());
        
        // Handle inventory isolation
        if (inventoryService != null) {
            inventoryService.onWorldChange(player, event.getFrom(), world);
        }
    }
}
