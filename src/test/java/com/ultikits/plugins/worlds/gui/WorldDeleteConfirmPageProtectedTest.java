package com.ultikits.plugins.worlds.gui;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;
import com.ultikits.plugins.worlds.config.WorldConfig;
import com.ultikits.plugins.worlds.entity.WorldSettings;
import com.ultikits.plugins.worlds.service.WorldService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.Query;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Gate-1 WR-04: {@link WorldService#deleteWorld(String)}'s own javadoc names
 * {@code WorldDeleteConfirmPage#onConfirm} as the reason the protected-world refusal lives in the
 * service rather than in the command -- and that page was the one caller that could not show the
 * refusal, sending the generic {@code command.delete.failed} instead, which is indistinguishable
 * from a locked file or a partial delete.
 *
 * <p>A real {@link WorldService} sits behind the page here, so the message the page shows and the
 * refusal the service applies are exercised as one path.
 *
 * <p>This does not wire the page into any command -- that is {@code UltiKits/UltiWorlds#19}, in
 * wave 2, and is explicitly out of scope for this pull request. It only makes the message the page
 * would show correct.
 *
 * @author wisdomme
 * @version 2.0.0
 */
@SuppressWarnings("PMD.AvoidAccessibilityAlteration") // onConfirm is protected; the page has no public entry point
@DisplayName("WorldDeleteConfirmPage protected-world message (UltiWorlds#20, gate-1 WR-04)")
class WorldDeleteConfirmPageProtectedTest {

    private WorldService worldService;
    private WorldConfig mockConfig;
    private UltiToolsPlugin mockPlugin;
    private Player mockPlayer;
    private DataOperator<WorldSettings> mockDataOperator;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        UltiWorldsTestHelper.setUp();
        mockPlugin = UltiWorldsTestHelper.getMockPlugin();
        mockPlayer = UltiWorldsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());

        mockConfig = UltiWorldsTestHelper.createDefaultConfig();
        mockDataOperator = mock(DataOperator.class);

        worldService = new WorldService();
        UltiWorldsTestHelper.setField(worldService, "config", mockConfig);
        UltiWorldsTestHelper.setField(worldService, "dataOperator", mockDataOperator);
        UltiWorldsTestHelper.setField(worldService, "plugin", mockPlugin);

        Query<WorldSettings> mockQuery = mock(Query.class);
        when(mockDataOperator.query()).thenReturn(mockQuery);
        when(mockQuery.where(anyString())).thenReturn(mockQuery);
        when(mockQuery.eq(any())).thenReturn(mockQuery);
        when(mockQuery.first()).thenReturn(null);
        when(mockQuery.delete()).thenReturn(0);
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiWorldsTestHelper.tearDown();
    }

    private void invokeOnConfirm(String worldName) throws Exception {
        WorldDeleteConfirmPage page =
                new WorldDeleteConfirmPage(mockPlayer, worldService, worldName, mockPlugin);
        Method onConfirm =
                WorldDeleteConfirmPage.class.getDeclaredMethod("onConfirm", InventoryClickEvent.class);
        onConfirm.setAccessible(true); // NOPMD - the page exposes no public entry point
        onConfirm.invoke(page, mock(InventoryClickEvent.class));
    }

    @Test
    @DisplayName("onConfirm reports world.delete.protected, not the generic failure, for a protected world")
    void onConfirmReportsTheProtectedMessage() throws Exception {
        when(mockConfig.getDefaultWorld()).thenReturn("world");
        when(mockConfig.getProtectedWorlds())
                .thenReturn(Arrays.asList("world", "world_nether", "world_the_end"));

        invokeOnConfirm("world_nether");

        verify(mockPlugin).i18n("world.delete.protected");
        verify(mockPlugin, never()).i18n("command.delete.failed");
        verify(mockPlugin, never()).i18n("command.delete.success");
    }

    @Test
    @DisplayName("onConfirm still reports world.delete.default for the default world, in any case")
    void onConfirmKeepsTheDefaultWorldMessage() throws Exception {
        when(mockConfig.getDefaultWorld()).thenReturn("world");
        when(mockConfig.getProtectedWorlds()).thenReturn(Collections.<String>emptyList());

        invokeOnConfirm("WORLD");

        verify(mockPlugin).i18n("world.delete.default");
        verify(mockPlugin, never()).i18n("world.delete.protected");
    }
}
