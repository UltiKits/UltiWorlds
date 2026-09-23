package com.ultikits.plugins.worlds.gui;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;
import com.ultikits.plugins.worlds.config.WorldConfig;
import com.ultikits.plugins.worlds.service.WorldService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code UltiKits/UltiWorlds#19}: the two guards the confirm button itself carries, now that
 * {@code /world delete} reaches this page. The page stays open for as long as the player leaves it
 * open, so what was true when the command ran is re-checked at the moment of the irreversible step.
 *
 * <ul>
 *   <li>A page deletes at most once: a second confirm on the same page never calls the service
 *       again.</li>
 *   <li>A player who no longer holds {@code ultiworlds.admin.delete} when pressing confirm deletes
 *       nothing, and is told so.</li>
 * </ul>
 *
 * <p>Each test has a control in the same class: the first confirm does delete, and a player who
 * still holds the permission does delete -- so neither guard passes by refusing everything.
 *
 * @author wisdomme
 * @version 2.0.0
 */
@DisplayName("WorldDeleteConfirmPage confirm-time guards (UltiWorlds#19)")
class WorldDeleteConfirmPageGuardsTest {

    private WorldService worldService;
    private UltiToolsPlugin plugin;
    private Player player;
    private java.io.File container;
    private org.mockito.MockedStatic<org.bukkit.Bukkit> bukkit;

    @BeforeEach
    void setUp() throws Exception {
        UltiWorldsTestHelper.setUp();
        container = java.nio.file.Files.createTempDirectory("p17w2guards").toFile();
        bukkit = DeleteConfirmPageDriver.worldContainerIn(container);
        plugin = UltiWorldsTestHelper.getMockPlugin();
        worldService = mock(WorldService.class);
        WorldConfig config = mock(WorldConfig.class);
        when(config.getDefaultWorld()).thenReturn("world");
        when(worldService.getConfig()).thenReturn(config);
        when(worldService.isDeleteProtected(anyString())).thenReturn(false);
        when(worldService.deleteWorld("scratchw")).thenReturn(true);
        player = UltiWorldsTestHelper.createMockPlayer("Admin", UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws Exception {
        bukkit.close();
        container.delete();
        UltiWorldsTestHelper.tearDown();
    }

    @Test
    @DisplayName("a second confirm on the same page never calls the service again")
    void deletesAtMostOnce() {
        WorldDeleteConfirmPage page = new WorldDeleteConfirmPage(player, worldService, "scratchw", plugin);

        DeleteConfirmPageDriver.confirm(page);
        verify(worldService, times(1)).deleteWorld("scratchw");

        DeleteConfirmPageDriver.confirm(page);
        verify(worldService, times(1)).deleteWorld("scratchw");
        verify(plugin, times(1)).i18n("command.delete.success");
    }

    @Test
    @DisplayName("confirm without ultiworlds.admin.delete deletes nothing and says why")
    void confirmWithoutThePermissionDeletesNothing() {
        when(player.hasPermission("ultiworlds.admin.delete")).thenReturn(false);
        WorldDeleteConfirmPage page = new WorldDeleteConfirmPage(player, worldService, "scratchw", plugin);

        DeleteConfirmPageDriver.confirm(page);

        verify(worldService, never()).deleteWorld(anyString());
        verify(plugin).i18n("error.no_permission");
    }

    @Test
    @DisplayName("after the window has closed, a later OK deletes nothing (gate-1 WR-01)")
    void anOkAfterCloseDeletesNothing() {
        WorldDeleteConfirmPage page = new WorldDeleteConfirmPage(player, worldService, "scratchw", plugin);

        DeleteConfirmPageDriver.close(page);
        DeleteConfirmPageDriver.confirm(page);

        verify(worldService, never()).deleteWorld(anyString());
    }

    @Test
    @DisplayName("a click on the OK slot's index in the player's own inventory deletes nothing (gate-1 WR-01)")
    void anOkSlotClickInThePlayersOwnInventoryDeletesNothing() {
        WorldDeleteConfirmPage page = new WorldDeleteConfirmPage(player, worldService, "scratchw", plugin);

        DeleteConfirmPageDriver.clickOkSlotInPlayersOwnInventory(page);

        verify(worldService, never()).deleteWorld(anyString());
    }

    @Test
    @DisplayName("control: an OK click in the page's own inventory deletes, and a later stray click does not count against it")
    void anOkClickInThePagesOwnInventoryDeletes() {
        WorldDeleteConfirmPage page = new WorldDeleteConfirmPage(player, worldService, "scratchw", plugin);
        DeleteConfirmPageDriver.clickOkSlotInPlayersOwnInventory(page);

        DeleteConfirmPageDriver.confirm(page);

        verify(worldService, times(1)).deleteWorld("scratchw");
    }

    @Test
    @DisplayName("control: confirm with ultiworlds.admin.delete deletes")
    void confirmWithThePermissionDeletes() {
        when(player.hasPermission("ultiworlds.admin.delete")).thenReturn(true);
        WorldDeleteConfirmPage page = new WorldDeleteConfirmPage(player, worldService, "scratchw", plugin);

        DeleteConfirmPageDriver.confirm(page);

        verify(worldService).deleteWorld("scratchw");
        verify(plugin, never()).i18n("error.no_permission");
    }
}
