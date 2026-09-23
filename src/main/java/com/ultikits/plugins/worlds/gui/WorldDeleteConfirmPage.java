package com.ultikits.plugins.worlds.gui;

import com.ultikits.plugins.worlds.service.WorldDeleteTarget;
import com.ultikits.plugins.worlds.service.WorldService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.abstracts.gui.BaseConfirmationPage;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

/**
 * Confirmation page for world deletion, opened by {@code /world delete <name>}. Nothing is deleted
 * until the player presses confirm; cancel and closing the page delete nothing
 * (UltiKits/UltiWorlds#19).
 *
 * <p>The page can stay open indefinitely, so {@link #onConfirm} re-checks, at the moment of the
 * irreversible step, everything the command checked when it opened the page: the delete permission,
 * the default world, and {@code protected_worlds}; and it refuses when the world known by that name
 * is no longer the one the page was opened for -- deleted and recreated under the same name in
 * between ({@link WorldDeleteTarget}). A page deletes at most once.
 *
 * <p>The page disarms itself: once it has been confirmed or closed, OK does nothing, and OK acts
 * only on a click that landed in this page's own inventory. That safety does not rest on
 * obliviate-invs dropping the page from its open-GUI table on close -- if anything in the close
 * chain threw, obliviate would keep routing the player's later clicks here by slot number alone,
 * including a click in the player's own inventory at the OK slot's index (gate-1 WR-01).
 *
 * @author wisdomme
 * @version 2.0.0
 */
public class WorldDeleteConfirmPage extends BaseConfirmationPage {

    /** The node {@code /world delete} requires; re-checked on confirm. */
    private static final String DELETE_PERMISSION = "ultiworlds.admin.delete";

    private final WorldService worldService;
    private final UltiToolsPlugin plugin;
    private final String worldName;

    /** The world this page was opened for; OK refuses if the name now means another one. */
    private final WorldDeleteTarget target;

    /**
     * Set by the first confirm and by closing the page, so neither a second click nor a click
     * routed here after the page closed can run a deletion.
     */
    private boolean disarmed;

    public WorldDeleteConfirmPage(Player player, WorldService worldService, String worldName, UltiToolsPlugin plugin) {
        super(player, "delete-" + worldName, plugin.i18n("gui.delete.title").replace("%world%", worldName), 3);
        this.worldService = worldService;
        this.plugin = plugin;
        this.worldName = worldName;
        this.target = WorldDeleteTarget.capture(worldName);
    }
    
    @Override
    protected void onConfirm(InventoryClickEvent event) {
        if (disarmed || !isOnThisPage(event)) {
            return;
        }
        disarmed = true;

        if (!player.hasPermission(DELETE_PERMISSION)) {
            player.sendMessage(i18n("error.no_permission"));
            return;
        }

        // Case-insensitive because CraftServer#getWorld resolves a world name that way, so an
        // exact comparison here would let a differently-cased default world through this guard.
        if (worldName.equalsIgnoreCase(worldService.getConfig().getDefaultWorld())) {
            player.sendMessage(i18n("world.delete.default"));
            return;
        }

        // WorldService#deleteWorld refuses a protected world on its own; asking here only decides
        // which message this page shows, so that a protected refusal is not reported as the generic
        // "failed to delete", which would be indistinguishable from a locked file.
        if (worldService.isDeleteProtected(worldName)) {
            player.sendMessage(i18n("world.delete.protected").replace("{WORLD}", worldName));
            return;
        }

        // The page may have been open for a long time: a world deleted and recreated under this
        // name meanwhile is not the world the player was asked about.
        if (!target.equals(WorldDeleteTarget.capture(worldName))) {
            player.sendMessage(i18n("world.delete.changed").replace("{WORLD}", worldName));
            return;
        }

        boolean success = worldService.deleteWorld(worldName);
        
        if (success) {
            player.sendMessage(i18n("command.delete.success").replace("%world%", worldName));
        } else {
            player.sendMessage(i18n("command.delete.failed").replace("%world%", worldName));
        }
    }
    
    /**
     * Disarm before anything that could throw, then let the framework and obliviate-invs do their
     * own close handling.
     */
    @Override
    public void onClose(InventoryCloseEvent event) {
        disarmed = true;
        super.onClose(event);
    }

    /** Whether a click landed in this page's own (top) inventory, as opposed to the player's. */
    private boolean isOnThisPage(InventoryClickEvent event) {
        Inventory page = getInventory();
        return page != null && event.getClickedInventory() == page;
    }

    @Override
    protected void onCancel(InventoryClickEvent event) {
        player.sendMessage(i18n("command.delete.cancelled"));
    }
    
    /**
     * Get i18n message from plugin.
     */
    private String i18n(String key) {
        return plugin.i18n(key);
    }
}
