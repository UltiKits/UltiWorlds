package com.ultikits.plugins.worlds.commands;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;
import com.ultikits.plugins.worlds.config.WorldConfig;
import com.ultikits.plugins.worlds.entity.WorldSettings;
import com.ultikits.plugins.worlds.i18n.CatalogueText;
import com.ultikits.plugins.worlds.service.WorldService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A value an operator typed -- a display name, a description -- is inserted into a line once, as
 * written: a placeholder it happens to contain is not filled by a later substitution
 * (UltiKits/UltiWorlds#43, maintainer decision 2026-09-27: one-pass fill, swept across modules).
 */
@DisplayName("Command lines insert operator text as written (UltiKits/UltiWorlds#43)")
class WorldCommandPlaceholderTest {

    private WorldCommand command;
    private WorldService worldService;
    private Player player;
    private World world;
    private WorldSettings settings;

    @BeforeEach
    void setUp() throws Exception {
        UltiWorldsTestHelper.setUp();
        UltiToolsPlugin plugin = UltiWorldsTestHelper.getMockPlugin();
        when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        command = new WorldCommand();
        worldService = mock(WorldService.class);
        UltiWorldsTestHelper.setField(command, "worldService", worldService);
        UltiWorldsTestHelper.setField(command, "plugin", plugin);
        WorldConfig config = UltiWorldsTestHelper.createDefaultConfig();
        when(worldService.getConfig()).thenReturn(config);
        player = UltiWorldsTestHelper.createMockPlayer("Admin", UUID.randomUUID());
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.getPlayers()).thenReturn(Collections.emptyList());
        settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
        when(worldService.getOrCreateSettings("world")).thenReturn(settings);
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiWorldsTestHelper.tearDown();
    }

    @Test
    @DisplayName("/world list shows a display name containing {PLAYERS} as written")
    void listDisplayName() {
        settings.setDisplayName("Hub {PLAYERS}");
        when(worldService.getVisibleWorlds()).thenReturn(Collections.singletonList(world));
        command.listWorlds(player);
        verify(player).sendMessage("§7- §fHub {PLAYERS} §7(0 players)");
    }

    @Test
    @DisplayName("/world set shows a value containing {WORLD} as written")
    void setValue() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            command.setWorldOption(player, "world", "description", "about {WORLD}");
        }
        verify(player).sendMessage("§aSet description = about {WORLD} for world world");
    }
}
