package com.ultikits.plugins.worlds.service;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;
import com.ultikits.plugins.worlds.config.WorldConfig;
import com.ultikits.plugins.worlds.entity.WorldSettings;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.Query;

import org.bukkit.Bukkit;
import org.bukkit.Location;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * {@code /world delete} typed in another letter case removes the world's own folder
 * (UltiKits/UltiWorlds#52). {@code Bukkit#getWorld} finds a loaded world ignoring case, but the
 * folder was looked up under the typed text: on a case-sensitive filesystem there is no such
 * folder, nothing was removed, and the deletion was reported as done.
 *
 * <p>The folder is a real temporary directory, so the assertion reads the filesystem. On a
 * case-insensitive filesystem the typed text resolves to the real folder and the scenario cannot
 * go wrong, which is why the test names the world's own folder explicitly.
 */
@DisplayName("/world delete removes the world's own folder when the name is typed in another case (UltiWorlds#52)")
class WorldServiceDeleteFolderCaseTest {

    private WorldService worldService;
    private WorldConfig mockConfig;
    private File container;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        UltiWorldsTestHelper.setUp();
        UltiToolsPlugin mockPlugin = UltiWorldsTestHelper.getMockPlugin();
        mockConfig = UltiWorldsTestHelper.createDefaultConfig();
        when(mockConfig.getDefaultWorld()).thenReturn("lobby");
        when(mockConfig.getProtectedWorlds()).thenReturn(Collections.<String>emptyList());
        DataOperator<WorldSettings> dataOperator = mock(DataOperator.class);
        Query<WorldSettings> query = mock(Query.class);
        when(dataOperator.query()).thenReturn(query);
        when(query.where(anyString())).thenReturn(query);
        when(query.eq(any())).thenReturn(query);
        when(query.delete()).thenReturn(1);

        worldService = new WorldService();
        UltiWorldsTestHelper.setField(worldService, "config", mockConfig);
        UltiWorldsTestHelper.setField(worldService, "dataOperator", dataOperator);
        UltiWorldsTestHelper.setField(worldService, "plugin", mockPlugin);

        container = Files.createTempDirectory("p17wfu52").toFile();
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

    private File worldFolderWithARegionFile(String folderName) throws IOException {
        File folder = new File(container, folderName);
        assertThat(new File(folder, "region").mkdirs()).isTrue();
        assertThat(new File(folder, "region/r.0.0.mca").createNewFile()).isTrue();
        return folder;
    }

    @Test
    @DisplayName("a loaded world typed in another case: its own folder is removed, and the deletion is reported")
    void loadedWorldTypedInAnotherCase() throws IOException {
        File folder = worldFolderWithARegionFile("myworld");
        World myworld = mock(World.class);
        when(myworld.getName()).thenReturn("myworld");
        when(myworld.getPlayers()).thenReturn(Collections.<Player>emptyList());
        World lobby = mock(World.class);
        when(lobby.getSpawnLocation()).thenReturn(mock(Location.class));
        boolean deleted;
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("MYWORLD")).thenReturn(myworld);
            bukkit.when(() -> Bukkit.getWorld("myworld")).thenReturn(myworld);
            bukkit.when(() -> Bukkit.getWorld("lobby")).thenReturn(lobby);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
            bukkit.when(() -> Bukkit.unloadWorld(any(World.class), any(Boolean.class))).thenReturn(true);

            deleted = worldService.deleteWorld("MYWORLD");
        }

        assertThat(deleted).isTrue();
        assertThat(folder).doesNotExist();
    }

    @Test
    @DisplayName("an unloaded world is still looked up under exactly the name given")
    void unloadedWorldKeepsTheTypedName() throws IOException {
        File folder = worldFolderWithARegionFile("oldworld");
        boolean deleted;
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container);

            deleted = worldService.deleteWorld("oldworld");
        }

        assertThat(deleted).isTrue();
        assertThat(folder).doesNotExist();
    }
}
