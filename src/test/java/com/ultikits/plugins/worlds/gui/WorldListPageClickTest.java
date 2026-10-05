package com.ultikits.plugins.worlds.gui;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;
import com.ultikits.plugins.worlds.entity.WorldSettings;
import com.ultikits.plugins.worlds.service.WorldService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import mc.obliviate.inventory.Icon;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A click in the world list closes the window and teleports the viewer, and the teleport runs the
 * world's post-teleport commands as the console. Since UltiTools-Reborn#541 a module command body
 * runs inline at dispatch, so a post-teleport command that opens another module's window would now
 * open it inside the click event, where Paper refuses it. The teleport therefore runs one tick after
 * the click (UltiKits/UltiWorlds#50).
 */
@DisplayName("A click in the world list teleports after the click event (UltiWorlds#50)")
class WorldListPageClickTest {

    private WorldService worldService;
    private UltiToolsPlugin mockPlugin;
    private Player player;
    private World world;

    @BeforeEach
    void setUp() throws Exception {
        UltiWorldsTestHelper.setUp();
        mockPlugin = UltiWorldsTestHelper.getMockPlugin();
        // The registered plugin the module's scheduler calls are made for (the framework's own
        // plugin, "UltiTools", which the module finds by name).
        MockBukkit.createMockPlugin("UltiTools");
        worldService = mock(WorldService.class);
        player = UltiWorldsTestHelper.createMockPlayer("Clicker", UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);

        world = mock(World.class);
        when(world.getName()).thenReturn("world1");
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getPlayers()).thenReturn(Collections.<Player>emptyList());
        when(worldService.getVisibleWorlds()).thenReturn(Collections.singletonList(world));
        WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world1");
        when(worldService.getOrCreateSettings("world1")).thenReturn(settings);
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiWorldsTestHelper.tearDown();
    }

    @SuppressWarnings("unchecked")
    private Icon theWorldIcon() throws Exception {
        WorldListPage page = new WorldListPage(player, worldService, mockPlugin);
        Method method = WorldListPage.class.getDeclaredMethod("provideItems");
        method.setAccessible(true); // NOPMD - test reflection
        return ((List<Icon>) method.invoke(page)).get(0);
    }

    @Test
    @DisplayName("the teleport has not run when the click returns, and runs on the next tick")
    void teleportRunsAfterTheClickEvent() throws Exception {
        Icon icon = theWorldIcon();

        icon.getClickAction().accept(mock(InventoryClickEvent.class));

        verify(player).closeInventory();
        verify(worldService, never()).teleportToWorld(player, "world1");

        MockBukkit.getMock().getScheduler().performOneTick();

        verify(worldService).teleportToWorld(player, "world1");
    }

    @Test
    @DisplayName("a clicker who left before the next tick is not teleported, and no console command runs for the name")
    void leftBeforeTheNextTick() throws Exception {
        Icon icon = theWorldIcon();

        icon.getClickAction().accept(mock(InventoryClickEvent.class));
        when(player.isOnline()).thenReturn(false);
        MockBukkit.getMock().getScheduler().performOneTick();

        verify(worldService, never()).teleportToWorld(player, "world1");
    }
}
