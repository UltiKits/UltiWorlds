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
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code UltiKits/UltiWorlds#19}: a player's {@code /world delete <name>} no longer deletes on the
 * spot. It validates the name exactly as before, then opens {@link WorldDeleteConfirmPage}; only the
 * page's confirm button deletes, and cancelling or closing the page deletes nothing.
 *
 * <p>A real {@link WorldService} and a real world folder in a temporary world container sit behind
 * the command, so "deletes" and "deletes nothing" are read off the disk, not off a mock. The page is
 * built from the arguments the command itself passed (see {@link DeleteConfirmPageDriver}), so the
 * page a test confirms is the one the command asked for.
 *
 * @author wisdomme
 * @version 2.0.0
 */
@DisplayName("/world delete asks for confirmation (UltiWorlds#19)")
class WorldCommandDeleteConfirmationTest {

    private static final String WORLD = "scratchw";

    private WorldCommand command;
    private WorldService worldService;
    private WorldConfig mockConfig;
    private UltiToolsPlugin mockPlugin;
    private DataOperator<WorldSettings> mockDataOperator;
    private File container;
    private File worldFolder;
    private Player player;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        UltiWorldsTestHelper.setUp();
        mockPlugin = UltiWorldsTestHelper.getMockPlugin();

        mockConfig = UltiWorldsTestHelper.createDefaultConfig();
        when(mockConfig.getDefaultWorld()).thenReturn("world");
        when(mockConfig.getProtectedWorlds())
                .thenReturn(Arrays.asList("world", "world_nether", "world_the_end"));
        mockDataOperator = mock(DataOperator.class);
        Query<WorldSettings> mockQuery = mock(Query.class);
        when(mockDataOperator.query()).thenReturn(mockQuery);
        when(mockQuery.where(anyString())).thenReturn(mockQuery);
        when(mockQuery.eq(any())).thenReturn(mockQuery);
        when(mockQuery.first()).thenReturn(null);
        when(mockQuery.delete()).thenReturn(0);

        worldService = new WorldService();
        UltiWorldsTestHelper.setField(worldService, "config", mockConfig);
        UltiWorldsTestHelper.setField(worldService, "dataOperator", mockDataOperator);
        UltiWorldsTestHelper.setField(worldService, "plugin", mockPlugin);

        command = new WorldCommand();
        UltiWorldsTestHelper.setField(command, "worldService", worldService);
        UltiWorldsTestHelper.setField(command, "plugin", mockPlugin);

        container = Files.createTempDirectory("p17w2confirm").toFile();
        worldFolder = new File(container, WORLD);
        assertThat(new File(worldFolder, "region").mkdirs()).isTrue();

        player = UltiWorldsTestHelper.createMockPlayer("Admin", UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws Exception {
        deleteRecursively(container);
        UltiWorldsTestHelper.tearDown();
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    /** Run {@code /world delete <name>} as {@link #player}; return the arguments of every page it built. */
    private List<List<Object>> runDelete(MockedStatic<Bukkit> bukkit, String name) {
        List<List<Object>> opened = new ArrayList<>();
        try (MockedConstruction<WorldDeleteConfirmPage> pages = DeleteConfirmPageDriver.intercept(opened)) {
            command.deleteWorld(player, name);
            for (WorldDeleteConfirmPage page : pages.constructed()) {
                verify(page).open();
            }
        }
        return opened;
    }

    /** Run {@code /world delete} on {@link #WORLD} and return the one page it opened, rebuilt for real. */
    private WorldDeleteConfirmPage openThePage(MockedStatic<Bukkit> bukkit) {
        List<List<Object>> opened = runDelete(bukkit, WORLD);
        assertThat(opened).as("exactly one confirmation page opened").hasSize(1);
        return DeleteConfirmPageDriver.rebuild(opened.get(0));
    }

    private MockedStatic<Bukkit> bukkitWithContainer() {
        MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.getWorld(WORLD)).thenReturn(null);
        bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
        return bukkit;
    }

    @Test
    @DisplayName("a player's /world delete opens the confirmation page for that world and deletes nothing yet")
    void deleteOpensTheConfirmationPageAndDeletesNothingYet() {
        try (MockedStatic<Bukkit> bukkit = bukkitWithContainer()) {
            List<List<Object>> opened = runDelete(bukkit, WORLD);

            assertThat(opened).hasSize(1);
            assertThat(opened.get(0)).containsExactly(player, worldService, WORLD, mockPlugin);
            assertThat(worldFolder).exists();
            verify(mockPlugin, never()).i18n("world.delete.deleting");
            verify(mockPlugin, never()).i18n("world.delete.success");
            verify(mockPlugin, never()).i18n("command.delete.success");
        }
    }

    @Test
    @DisplayName("confirming that page deletes the world and reports it")
    void confirmingDeletesTheWorldAndReportsIt() {
        try (MockedStatic<Bukkit> bukkit = bukkitWithContainer()) {
            WorldDeleteConfirmPage page = openThePage(bukkit);

            DeleteConfirmPageDriver.confirm(page);

            assertThat(worldFolder).doesNotExist();
            verify(mockPlugin).i18n("command.delete.success");
            verify(mockPlugin, never()).i18n("command.delete.failed");
        }
    }

    @Test
    @DisplayName("cancelling that page deletes nothing and says so")
    void cancellingDeletesNothing() {
        try (MockedStatic<Bukkit> bukkit = bukkitWithContainer()) {
            WorldDeleteConfirmPage page = openThePage(bukkit);

            DeleteConfirmPageDriver.cancel(page);

            assertThat(worldFolder).exists();
            assertThat(new File(worldFolder, "region")).exists();
            verify(mockPlugin).i18n("command.delete.cancelled");
            verify(mockPlugin, never()).i18n("command.delete.success");
        }
    }

    @Test
    @DisplayName("closing that page without pressing a button deletes nothing")
    void closingDeletesNothing() {
        try (MockedStatic<Bukkit> bukkit = bukkitWithContainer()) {
            WorldDeleteConfirmPage page = openThePage(bukkit);

            DeleteConfirmPageDriver.close(page);

            assertThat(worldFolder).exists();
            assertThat(new File(worldFolder, "region")).exists();
            verify(mockPlugin, never()).i18n("command.delete.success");
            verify(mockPlugin, never()).i18n("command.delete.failed");
        }
    }

    @Test
    @DisplayName("a world added to protected_worlds while the page is open is still refused on confirm")
    void aWorldProtectedAfterThePageOpenedIsRefusedOnConfirm() {
        try (MockedStatic<Bukkit> bukkit = bukkitWithContainer()) {
            WorldDeleteConfirmPage page = openThePage(bukkit);
            when(mockConfig.getProtectedWorlds()).thenReturn(Arrays.asList("world", WORLD));

            DeleteConfirmPageDriver.confirm(page);

            assertThat(worldFolder).exists();
            assertThat(new File(worldFolder, "region")).exists();
            verify(mockPlugin).i18n("world.delete.protected");
            verify(mockPlugin, never()).i18n("command.delete.success");
        }
    }

    /** Write a Bukkit-style {@code uid.dat} (two big-endian longs) into a world folder. */
    static void writeUid(File folder, UUID uid) throws java.io.IOException {
        try (java.io.DataOutputStream out = new java.io.DataOutputStream(
                new java.io.FileOutputStream(new File(folder, "uid.dat")))) {
            out.writeLong(uid.getMostSignificantBits());
            out.writeLong(uid.getLeastSignificantBits());
        }
    }

    /** Delete the world folder and create a different world under the same name. */
    private File replaceWorldFolder() throws java.io.IOException {
        deleteRecursively(worldFolder);
        assertThat(new File(worldFolder, "region").mkdirs()).isTrue();
        writeUid(worldFolder, UUID.randomUUID());
        File marker = new File(worldFolder, "replacement.marker");
        assertThat(marker.createNewFile()).isTrue();
        return marker;
    }

    @Test
    @DisplayName("a world deleted and recreated under the same name while the page is open is not deleted on confirm")
    void aWorldReplacedWhileThePageIsOpenIsNotDeleted() throws Exception {
        writeUid(worldFolder, UUID.randomUUID());
        try (MockedStatic<Bukkit> bukkit = bukkitWithContainer()) {
            WorldDeleteConfirmPage page = openThePage(bukkit);
            File marker = replaceWorldFolder();

            DeleteConfirmPageDriver.confirm(page);

            assertThat(marker).as("the replacement world survives").exists();
            verify(mockPlugin).i18n("world.delete.changed");
            verify(mockPlugin, never()).i18n("command.delete.success");
        }
    }

    @Test
    @DisplayName("a loaded world replaced by another loaded world of the same name is not deleted on confirm")
    void aLoadedWorldReplacedWhileThePageIsOpenIsNotDeleted() throws Exception {
        try (MockedStatic<Bukkit> bukkit = bukkitWithContainer()) {
            org.bukkit.World first = mock(org.bukkit.World.class);
            when(first.getUID()).thenReturn(UUID.randomUUID());
            bukkit.when(() -> Bukkit.getWorld(WORLD)).thenReturn(first);
            WorldDeleteConfirmPage page = openThePage(bukkit);

            org.bukkit.World second = mock(org.bukkit.World.class);
            when(second.getUID()).thenReturn(UUID.randomUUID());
            when(second.getPlayers()).thenReturn(java.util.Collections.<Player>emptyList());
            bukkit.when(() -> Bukkit.getWorld(WORLD)).thenReturn(second);
            // Everything an unguarded deletion of the replacement needs is stubbed, so without the
            // identity check this test fails on the surviving folder, not on an unstubbed call.
            org.bukkit.World fallback = mock(org.bukkit.World.class);
            when(fallback.getSpawnLocation()).thenReturn(mock(org.bukkit.Location.class));
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(fallback);
            bukkit.when(Bukkit::getWorlds).thenReturn(java.util.Collections.singletonList(fallback));
            bukkit.when(() -> Bukkit.unloadWorld(any(org.bukkit.World.class), any(Boolean.class))).thenReturn(true);
            DeleteConfirmPageDriver.confirm(page);

            assertThat(worldFolder).exists();
            verify(mockPlugin).i18n("world.delete.changed");
            verify(mockPlugin, never()).i18n("command.delete.success");
        }
    }

    @Test
    @DisplayName("control: a world with its uid.dat unchanged is deleted on confirm")
    void aWorldWhoseIdentityIsUnchangedIsDeleted() throws Exception {
        writeUid(worldFolder, UUID.randomUUID());
        try (MockedStatic<Bukkit> bukkit = bukkitWithContainer()) {
            WorldDeleteConfirmPage page = openThePage(bukkit);

            DeleteConfirmPageDriver.confirm(page);

            assertThat(worldFolder).doesNotExist();
            verify(mockPlugin, never()).i18n("world.delete.changed");
            verify(mockPlugin).i18n("command.delete.success");
        }
    }

    @Test
    @DisplayName("a second confirm on the same page does not run the deletion again")
    void aSecondConfirmDoesNotDeleteAgain() {
        try (MockedStatic<Bukkit> bukkit = bukkitWithContainer()) {
            WorldDeleteConfirmPage page = openThePage(bukkit);

            DeleteConfirmPageDriver.confirm(page);
            DeleteConfirmPageDriver.confirm(page);

            assertThat(worldFolder).doesNotExist();
            // A second run would find no folder and report the generic failure.
            verify(mockPlugin, times(1)).i18n("command.delete.success");
            verify(mockPlugin, never()).i18n("command.delete.failed");
        }
    }

    @Test
    @DisplayName("losing the delete permission while the page is open means confirm deletes nothing")
    void confirmAfterThePermissionIsRevokedDeletesNothing() {
        try (MockedStatic<Bukkit> bukkit = bukkitWithContainer()) {
            WorldDeleteConfirmPage page = openThePage(bukkit);
            when(player.hasPermission("ultiworlds.admin.delete")).thenReturn(false);

            DeleteConfirmPageDriver.confirm(page);

            assertThat(worldFolder).exists();
            verify(mockPlugin).i18n("error.no_permission");
            verify(mockPlugin, never()).i18n("command.delete.success");
        }
    }

    @Test
    @DisplayName("every refusal the command already made still happens before any page opens")
    void refusalsNeverOpenThePage() {
        File protectedFolder = new File(container, "world_nether");
        assertThat(protectedFolder.mkdirs()).isTrue();
        try (MockedStatic<Bukkit> bukkit = bukkitWithContainer()) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(mock(org.bukkit.World.class));
            bukkit.when(() -> Bukkit.getWorld("world_nether")).thenReturn(null);
            bukkit.when(() -> Bukkit.getWorld("ghost")).thenReturn(null);

            assertThat(runDelete(bukkit, "world")).as("default world").isEmpty();
            assertThat(runDelete(bukkit, "world_nether")).as("protected world").isEmpty();
            assertThat(runDelete(bukkit, "ghost")).as("no such world").isEmpty();
            when(player.hasPermission("ultiworlds.admin.delete")).thenReturn(false);
            assertThat(runDelete(bukkit, WORLD)).as("no permission").isEmpty();

            verify(mockPlugin).i18n("world.delete.default");
            verify(mockPlugin).i18n("world.delete.protected");
            verify(mockPlugin).i18n("world.not_found");
            verify(mockPlugin).i18n("error.no_permission");
            assertThat(worldFolder).exists();
            assertThat(protectedFolder).exists();
        }
        // Control for the instrument: the same helper does see a page when the command opens one.
        try (MockedStatic<Bukkit> bukkit = bukkitWithContainer()) {
            when(player.hasPermission("ultiworlds.admin.delete")).thenReturn(true);
            assertThat(runDelete(bukkit, WORLD)).hasSize(1);
        }
    }
}
