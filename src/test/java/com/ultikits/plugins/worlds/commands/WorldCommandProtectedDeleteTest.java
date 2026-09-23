package com.ultikits.plugins.worlds.commands;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;
import com.ultikits.plugins.worlds.config.WorldConfig;
import com.ultikits.plugins.worlds.entity.WorldSettings;
import com.ultikits.plugins.worlds.gui.DeleteConfirmPageDriver;
import com.ultikits.plugins.worlds.gui.WorldDeleteConfirmPage;
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
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
 * Command-surface half of {@code UltiKits/UltiWorlds#20}: the refusal that actually protects the
 * world lives in {@link WorldService#deleteWorld(String)} (proven by
 * {@code WorldServiceProtectedDeleteTest}); this class proves the operator is told <em>why</em> the
 * deletion was refused, through the dedicated {@code world.delete.protected} message rather than
 * the generic {@code world.delete.failed} one.
 *
 * <p>Unlike the other {@code WorldCommand} test classes this one wires a <em>real</em>
 * {@link WorldService} behind the command rather than a mock, so the message the command sends and
 * the guard the service applies are exercised as one path. A mocked service would let this class
 * pass while the two disagreed.
 *
 * @author wisdomme
 * @version 2.0.0
 */
@DisplayName("WorldCommand protected-world deletion message (UltiWorlds#20)")
class WorldCommandProtectedDeleteTest {

    private WorldCommand command;
    private WorldService worldService;
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

        worldService = new WorldService();
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
    private Query<WorldSettings> stubQueryChain() {
        Query<WorldSettings> mockQuery = mock(Query.class);
        when(mockDataOperator.query()).thenReturn(mockQuery);
        when(mockQuery.where(anyString())).thenReturn(mockQuery);
        when(mockQuery.eq(any())).thenReturn(mockQuery);
        when(mockQuery.first()).thenReturn(null);
        when(mockQuery.delete()).thenReturn(0);
        return mockQuery;
    }

    /**
     * Since UltiKits/UltiWorlds#19 {@code /world delete} opens {@link WorldDeleteConfirmPage} and
     * the page's confirm button deletes. Runs the command, then confirms on a real page built from
     * exactly the arguments the command passed; returns how many pages the command opened.
     */
    private int deleteAndConfirm(Player player, String name) {
        List<List<Object>> opened = new ArrayList<>();
        try (MockedConstruction<WorldDeleteConfirmPage> pages = DeleteConfirmPageDriver.intercept(opened)) {
            command.deleteWorld(player, name);
        }
        for (List<Object> arguments : opened) {
            DeleteConfirmPageDriver.confirm(DeleteConfirmPageDriver.rebuild(arguments));
        }
        return opened.size();
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
    @DisplayName("/world delete on a protected world reports world.delete.protected and never starts deleting")
    void deleteOnAProtectedWorldReportsTheProtectedMessage() throws IOException {
        File container = Files.createTempDirectory("p17w1cmd").toFile();
        File worldFolder = new File(container, "world_nether");
        assertThat(worldFolder.mkdirs()).isTrue();

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            World nether = mock(World.class);
            when(nether.getPlayers()).thenReturn(Collections.<Player>emptyList());
            World defaultWorld = mock(World.class);
            when(defaultWorld.getSpawnLocation()).thenReturn(mock(org.bukkit.Location.class));

            // Everything the unguarded deletion would need is stubbed, so the pre-fix run reaches
            // the real deletion and this test fails on the message and the surviving folder --
            // never on an incidental unstubbed call.
            bukkit.when(() -> Bukkit.getWorld("world_nether")).thenReturn(nether);
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(defaultWorld);
            bukkit.when(() -> Bukkit.unloadWorld(any(World.class), any(Boolean.class))).thenReturn(true);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
            when(mockConfig.getDefaultWorld()).thenReturn("world");
            when(mockConfig.getProtectedWorlds())
                    .thenReturn(Arrays.asList("world", "world_nether", "world_the_end"));
            stubQueryChain();

            Player player = UltiWorldsTestHelper.createMockPlayer("Admin", UUID.randomUUID());
            when(player.hasPermission("ultiworlds.admin.delete")).thenReturn(true);

            command.deleteWorld(player, "world_nether");

            verify(mockPlugin).i18n("world.delete.protected");
            // Never reached the deletion step at all -- not "tried and failed".
            verify(mockPlugin, never()).i18n("world.delete.deleting");
            verify(mockPlugin, never()).i18n("world.delete.success");
            assertThat(worldFolder).exists();
        } finally {
            deleteRecursively(container);
        }
    }

    @Test
    @DisplayName("/world delete on an unprotected world still deletes it")
    void deleteOnAnUnprotectedWorldStillDeletes() throws IOException {
        File container = Files.createTempDirectory("p17w1cmd").toFile();
        File worldFolder = new File(container, "scratchw");
        assertThat(worldFolder.mkdirs()).isTrue();

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("scratchw")).thenReturn(null);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
            when(mockConfig.getDefaultWorld()).thenReturn("world");
            when(mockConfig.getProtectedWorlds())
                    .thenReturn(Arrays.asList("world", "world_nether", "world_the_end"));
            stubQueryChain();

            Player player = UltiWorldsTestHelper.createMockPlayer("Admin", UUID.randomUUID());
            when(player.hasPermission("ultiworlds.admin.delete")).thenReturn(true);

            assertThat(deleteAndConfirm(player, "scratchw")).isEqualTo(1);

            // Control: the new refusal branch must not swallow ordinary deletions.
            verify(mockPlugin).i18n("command.delete.success");
            verify(mockPlugin, never()).i18n("world.delete.protected");
            assertThat(worldFolder).doesNotExist();
        } finally {
            deleteRecursively(container);
        }
    }

    @Test
    @DisplayName("/world delete on the default world still reports world.delete.default, not the protected message")
    void deleteOnTheDefaultWorldKeepsItsOwnMessage() throws IOException {
        File container = Files.createTempDirectory("p17w1cmd").toFile();
        File worldFolder = new File(container, "world");
        assertThat(worldFolder.mkdirs()).isTrue();

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(mock(World.class));
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
            when(mockConfig.getDefaultWorld()).thenReturn("world");
            when(mockConfig.getProtectedWorlds()).thenReturn(Collections.<String>emptyList());
            stubQueryChain();

            Player player = UltiWorldsTestHelper.createMockPlayer("Admin", UUID.randomUUID());
            when(player.hasPermission("ultiworlds.admin.delete")).thenReturn(true);

            command.deleteWorld(player, "world");

            verify(mockPlugin).i18n("world.delete.default");
            verify(mockPlugin, never()).i18n("world.delete.protected");
            assertThat(worldFolder).exists();
        } finally {
            deleteRecursively(container);
        }
    }

    @Test
    @DisplayName("/world delete on a link whose target is missing is not refused as nonexistent")
    void deleteOnADanglingLinkIsNotRefusedAsNonexistent() throws IOException {
        File container = Files.createTempDirectory("p17w1dangling").toFile();
        File link = new File(container, "danglingw");
        Files.createSymbolicLink(link.toPath(), new File(container, "never_created").toPath());

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("danglingw")).thenReturn(null);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
            when(mockConfig.getDefaultWorld()).thenReturn("world");
            when(mockConfig.getProtectedWorlds()).thenReturn(Collections.<String>emptyList());
            stubQueryChain();

            Player player = UltiWorldsTestHelper.createMockPlayer("Admin", UUID.randomUUID());
            when(player.hasPermission("ultiworlds.admin.delete")).thenReturn(true);

            assertThat(deleteAndConfirm(player, "danglingw")).isEqualTo(1);

            // The command's on-disk check followed the link, so an entry that plainly exists in the
            // container was reported to the operator as a world that does not exist, and the entry
            // stayed. Deleting and loading ask different questions of the same path -- "is there an
            // entry I can remove" against "is there world data I can load" -- and one
            // link-following call cannot answer both.
            verify(mockPlugin, never()).i18n("world.not_found");
            verify(mockPlugin).i18n("command.delete.success");
            assertThat(Files.isSymbolicLink(link.toPath())).isFalse();
        } finally {
            link.delete();
            deleteRecursively(container);
        }
    }

}
