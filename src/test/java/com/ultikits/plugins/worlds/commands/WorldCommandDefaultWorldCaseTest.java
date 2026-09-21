package com.ultikits.plugins.worlds.commands;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;
import com.ultikits.plugins.worlds.config.WorldConfig;
import com.ultikits.plugins.worlds.entity.WorldSettings;
import com.ultikits.plugins.worlds.service.WorldService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.Query;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Gate-1 WR-03: the two `default_world` guards on the command surface compared the operator's
 * argument with {@link String#equals(Object)}, while {@code CraftServer#getWorld} resolves a world
 * name as {@code name.toLowerCase(Locale.ROOT)} -- measured directly in the bytecode of
 * {@code paper-1.21.4.jar}, offsets 15-34. So a name typed in another case walked past the guard
 * and then resolved to the real world anyway.
 *
 * <p>These tests use a real {@link WorldService} behind the command, because the second scenario is
 * specifically about the command and the service disagreeing about which refusal applies.
 *
 * @author wisdomme
 * @version 2.0.0
 */
@DisplayName("WorldCommand default-world guards are case-insensitive (UltiWorlds#20, gate-1 WR-03)")
class WorldCommandDefaultWorldCaseTest {

    private WorldCommand command;
    private WorldConfig mockConfig;
    private UltiToolsPlugin mockPlugin;
    private DataOperator<WorldSettings> mockDataOperator;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        UltiWorldsTestHelper.setUp();
        mockPlugin = UltiWorldsTestHelper.getMockPlugin();

        mockConfig = UltiWorldsTestHelper.createDefaultConfig();
        mockDataOperator = mock(DataOperator.class);

        WorldService worldService = new WorldService();
        UltiWorldsTestHelper.setField(worldService, "config", mockConfig);
        UltiWorldsTestHelper.setField(worldService, "dataOperator", mockDataOperator);
        UltiWorldsTestHelper.setField(worldService, "plugin", mockPlugin);

        command = new WorldCommand();
        UltiWorldsTestHelper.setField(command, "worldService", worldService);
        UltiWorldsTestHelper.setField(command, "plugin", mockPlugin);
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiWorldsTestHelper.tearDown();
    }

    @SuppressWarnings("unchecked")
    private void stubQueryChain() {
        Query<WorldSettings> mockQuery = mock(Query.class);
        when(mockDataOperator.query()).thenReturn(mockQuery);
        when(mockQuery.where(anyString())).thenReturn(mockQuery);
        when(mockQuery.eq(any())).thenReturn(mockQuery);
        when(mockQuery.first()).thenReturn(null);
        when(mockQuery.delete()).thenReturn(0);
    }

    private void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    @Test
    @DisplayName("/world unload refuses the default world named in another case")
    void unloadRefusesTheDefaultWorldNamedInAnotherCase() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            World lobby = mock(World.class);
            when(lobby.getPlayers()).thenReturn(Collections.<Player>emptyList());
            // A default world that is NOT the server's primary overworld, and is empty: the
            // platform's own backstop (CraftServer#unloadWorld refuses only the primary overworld
            // or a world with players in it) does not catch this one.
            bukkit.when(() -> Bukkit.getWorld("LOBBY")).thenReturn(lobby);
            bukkit.when(() -> Bukkit.getWorld("lobby")).thenReturn(lobby);
            bukkit.when(() -> Bukkit.unloadWorld(any(World.class), any(Boolean.class))).thenReturn(true);
            when(mockConfig.getDefaultWorld()).thenReturn("lobby");

            Player player = UltiWorldsTestHelper.createMockPlayer("Admin", UUID.randomUUID());
            when(player.hasPermission("ultiworlds.admin.unload")).thenReturn(true);

            command.unloadWorld(player, "LOBBY");

            verify(mockPlugin).i18n("world.unload.default");
            bukkit.verify(() -> Bukkit.unloadWorld(any(World.class), any(Boolean.class)), never());
            verify(mockPlugin, never()).i18n("world.unload.success");
        }
    }

    @Test
    @DisplayName("/world delete on the default world named in another case says so, and does not blame protected_worlds")
    void deleteOnTheDefaultWorldNamedInAnotherCaseSaysSo() throws IOException {
        File container = Files.createTempDirectory("p17w1case").toFile();
        File worldFolder = new File(container, "world");
        assertThat(worldFolder.mkdirs()).isTrue();

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("WORLD")).thenReturn(mock(World.class));
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
            when(mockConfig.getDefaultWorld()).thenReturn("world");
            // Deliberately empty, so "listed in protected_worlds" would be a false statement about
            // the operator's own configuration.
            when(mockConfig.getProtectedWorlds()).thenReturn(Collections.<String>emptyList());
            stubQueryChain();

            Player player = UltiWorldsTestHelper.createMockPlayer("Admin", UUID.randomUUID());
            when(player.hasPermission("ultiworlds.admin.delete")).thenReturn(true);

            command.deleteWorld(player, "WORLD");

            verify(mockPlugin).i18n("world.delete.default");
            verify(mockPlugin, never()).i18n("world.delete.protected");
            verify(mockPlugin, never()).i18n("world.delete.deleting");
            assertThat(worldFolder).exists();
        } finally {
            deleteRecursively(container);
        }
    }
}
