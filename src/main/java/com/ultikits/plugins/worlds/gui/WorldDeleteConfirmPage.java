package com.ultikits.plugins.worlds.gui;

import com.ultikits.plugins.worlds.service.WorldService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.abstracts.gui.BaseConfirmationPage;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Confirmation page for world deletion, opened by {@code /world delete <name>}. Nothing is deleted
 * until the player presses confirm; cancel and closing the page delete nothing
 * (UltiKits/UltiWorlds#19).
 *
 * <p>The page can stay open indefinitely, so {@link #onConfirm} re-checks, at the moment of the
 * irreversible step, everything the command checked when it opened the page: the delete permission,
 * the default world, and {@code protected_worlds}. A page deletes at most once.
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

    /** Set by the first confirm, so a second click on the same page cannot run a second deletion. */
    private boolean confirmed;

    public WorldDeleteConfirmPage(Player player, WorldService worldService, String worldName, UltiToolsPlugin plugin) {
        super(player, "delete-" + worldName, plugin.i18n("gui.delete.title").replace("%world%", worldName), 3);
        this.worldService = worldService;
        this.plugin = plugin;
        this.worldName = worldName;
    }
    
    @Override
    protected void onConfirm(InventoryClickEvent event) {
        if (confirmed) {
            return;
        }
        confirmed = true;

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

        boolean success = worldService.deleteWorld(worldName);
        
        if (success) {
            player.sendMessage(i18n("command.delete.success").replace("%world%", worldName));
        } else {
            player.sendMessage(i18n("command.delete.failed").replace("%world%", worldName));
        }
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
