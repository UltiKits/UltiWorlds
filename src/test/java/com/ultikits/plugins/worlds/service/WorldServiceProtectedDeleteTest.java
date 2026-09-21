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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression guard for {@code UltiKits/UltiWorlds#20}: {@code protected_worlds}' own declared
 * comment promises "Worlds that cannot be auto-unloaded or deleted", but only the auto-unload half
 * was enforced -- {@link WorldService#deleteWorld(String)} never consulted the list, so
 * {@code /world delete world_nether} permanently removed a world the shipped defaults list as
 * protected.
 *
 * <p>Every refusal below is asserted at the <em>service</em> level rather than at the command,
 * because the command is not the only caller: {@code WorldDeleteConfirmPage#onConfirm} calls
 * {@link WorldService#deleteWorld(String)} directly. A guard living only in {@code WorldCommand}
 * would leave that path, and any future one, unprotected.
 *
 * <p>Each refusal test asserts an observable that the pre-fix code demonstrably did NOT produce --
 * a world folder that still exists, or {@link Bukkit#getWorld(String)} never being reached -- so
 * none of them can pass vacuously against the defect they describe. The final test is the opposite
 * control: an unprotected world is still deleted, so the guard cannot be "passing" by refusing
 * everything.
 *
 * @author wisdomme
 * @version 2.0.0
 */
@DisplayName("WorldService protected-world deletion (UltiWorlds#20)")
class WorldServiceProtectedDeleteTest {

    private WorldService worldService;
    private WorldConfig mockConfig;
    private DataOperator<WorldSettings> mockDataOperator;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        UltiWorldsTestHelper.setUp();
        UltiToolsPlugin mockPlugin = UltiWorldsTestHelper.getMockPlugin();

        worldService = new WorldService();
        mockConfig = UltiWorldsTestHelper.createDefaultConfig();
        mockDataOperator = mock(DataOperator.class);

        UltiWorldsTestHelper.setField(worldService, "config", mockConfig);
        UltiWorldsTestHelper.setField(worldService, "dataOperator", mockDataOperator);
        UltiWorldsTestHelper.setField(worldService, "plugin", mockPlugin);
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
     * Creates a throwaway world folder with one region file inside it, so "the folder survived"
     * can be asserted on real bytes rather than on an empty directory that a partial delete would
     * also leave behind.
     */
    private File createWorldFolderWithContent(String prefix) throws IOException {
        File worldFolder = Files.createTempDirectory(prefix).toFile();
        File region = new File(worldFolder, "region");
        assertThat(region.mkdir()).isTrue();
        assertThat(new File(region, "r.0.0.mca").createNewFile()).isTrue();
        return worldFolder;
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
    @DisplayName("deleteWorld refuses a world listed in protected_worlds and leaves its folder on disk")
    void deleteRefusesAWorldListedInProtectedWorlds() throws IOException {
        File worldFolder = createWorldFolderWithContent("p17_protected_world_");
        File regionFile = new File(new File(worldFolder, "region"), "r.0.0.mca");
        String worldName = worldFolder.getName();

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            when(mockConfig.getDefaultWorld()).thenReturn("world");
            when(mockConfig.getProtectedWorlds())
                    .thenReturn(Arrays.asList("world", "world_nether", worldName));

            // Unloaded but present on disk -- the exact shape /world delete supports.
            bukkit.when(() -> Bukkit.getWorld(worldName)).thenReturn(null);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(worldFolder.getParentFile());
            stubQueryChain();

            // Pre-assertion: the fixture really is on disk before the call, so "still exists"
            // below cannot pass because it was never there.
            assertThat(worldFolder).exists();
            assertThat(regionFile).exists();

            boolean result = worldService.deleteWorld(worldName);

            assertThat(result).isFalse();
            assertThat(worldFolder).exists();
            assertThat(regionFile).exists();
            // The settings row is kept too -- a refusal removes nothing at all.
            verify(mockDataOperator, never()).query();
        } finally {
            deleteRecursively(worldFolder);
        }
    }

    @Test
    @DisplayName("deleteWorld refuses a protected world whose name differs only by case")
    void deleteRefusesAProtectedWorldNamedInADifferentCase() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            when(mockConfig.getDefaultWorld()).thenReturn("world");
            when(mockConfig.getProtectedWorlds()).thenReturn(Arrays.asList("world_nether"));
            stubQueryChain();

            boolean result = worldService.deleteWorld("WORLD_Nether");

            assertThat(result).isFalse();
            // Refused before anything was looked up or removed. Bukkit resolves a world name
            // case-insensitively and a case-insensitive filesystem resolves the folder the same
            // way, so an exact-match guard here would be bypassable by typing the name in a
            // different case.
            bukkit.verify(() -> Bukkit.getWorld(anyString()), never());
            bukkit.verify(Bukkit::getWorldContainer, never());
            verify(mockDataOperator, never()).query();
        }
    }

    @Test
    @DisplayName("deleteWorld refuses the configured default world even when protected_worlds is empty")
    void deleteRefusesTheDefaultWorldAtTheServiceLevel() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            when(mockConfig.getDefaultWorld()).thenReturn("world");
            when(mockConfig.getProtectedWorlds()).thenReturn(Collections.<String>emptyList());
            stubQueryChain();

            boolean result = worldService.deleteWorld("world");

            assertThat(result).isFalse();
            bukkit.verify(() -> Bukkit.getWorld(anyString()), never());
            verify(mockDataOperator, never()).query();
        }
    }

    @Test
    @DisplayName("checkAutoUnloadEmptyWorlds skips a protected world whose name differs only by case")
    void autoUnloadSkipsAProtectedWorldNamedInADifferentCase() throws InterruptedException {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            when(mockConfig.isAutoUnloadEmptyWorlds()).thenReturn(true);
            when(mockConfig.getEmptyWorldUnloadAfter()).thenReturn(0);
            when(mockConfig.getDefaultWorld()).thenReturn("world");
            // The operator wrote the name in a different case than the live world carries.
            when(mockConfig.getProtectedWorlds()).thenReturn(Arrays.asList("MyWorld"));

            World emptyWorld = mock(World.class);
            when(emptyWorld.getName()).thenReturn("myworld");
            when(emptyWorld.getPlayers()).thenReturn(Collections.<org.bukkit.entity.Player>emptyList());

            World defaultWorld = mock(World.class);
            when(defaultWorld.getSpawnLocation()).thenReturn(mock(Location.class));

            bukkit.when(Bukkit::getWorlds).thenReturn(Collections.singletonList(emptyWorld));
            bukkit.when(() -> Bukkit.getWorld("myworld")).thenReturn(emptyWorld);
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(defaultWorld);
            bukkit.when(() -> Bukkit.unloadWorld(emptyWorld, true)).thenReturn(true);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("myworld");
            settings.setAutoUnload(true);
            Query<WorldSettings> mockQuery = stubQueryChain();
            when(mockQuery.first()).thenReturn(settings);

            // First pass starts the empty-world timer, second pass sees it expired.
            worldService.checkAutoUnloadEmptyWorlds();
            Thread.sleep(5);
            worldService.checkAutoUnloadEmptyWorlds();

            bukkit.verify(() -> Bukkit.unloadWorld(any(World.class), anyBoolean()), never());
        }
    }

    @Test
    @DisplayName("checkAutoUnloadEmptyWorlds still unloads a world that is not protected")
    void autoUnloadStillUnloadsAnUnprotectedWorld() throws InterruptedException {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            when(mockConfig.isAutoUnloadEmptyWorlds()).thenReturn(true);
            when(mockConfig.getEmptyWorldUnloadAfter()).thenReturn(0);
            when(mockConfig.getDefaultWorld()).thenReturn("world");
            when(mockConfig.getProtectedWorlds()).thenReturn(Arrays.asList("SomeOtherWorld"));

            World emptyWorld = mock(World.class);
            when(emptyWorld.getName()).thenReturn("myworld");
            when(emptyWorld.getPlayers()).thenReturn(Collections.<org.bukkit.entity.Player>emptyList());

            World defaultWorld = mock(World.class);
            when(defaultWorld.getSpawnLocation()).thenReturn(mock(Location.class));

            bukkit.when(Bukkit::getWorlds).thenReturn(Collections.singletonList(emptyWorld));
            bukkit.when(() -> Bukkit.getWorld("myworld")).thenReturn(emptyWorld);
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(defaultWorld);
            bukkit.when(() -> Bukkit.unloadWorld(emptyWorld, true)).thenReturn(true);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("myworld");
            settings.setAutoUnload(true);
            Query<WorldSettings> mockQuery = stubQueryChain();
            when(mockQuery.first()).thenReturn(settings);

            worldService.checkAutoUnloadEmptyWorlds();
            Thread.sleep(5);
            worldService.checkAutoUnloadEmptyWorlds();

            // Control for the case-insensitive comparison above: it must not start refusing
            // every world.
            bukkit.verify(() -> Bukkit.unloadWorld(emptyWorld, true));
        }
    }

    @Test
    @DisplayName("deleteWorld still deletes a world that is not protected")
    void deleteStillRemovesAnUnprotectedWorld() throws IOException {
        File worldFolder = createWorldFolderWithContent("p17_unprotected_world_");
        String worldName = worldFolder.getName();

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            when(mockConfig.getDefaultWorld()).thenReturn("world");
            when(mockConfig.getProtectedWorlds())
                    .thenReturn(Arrays.asList("world", "world_nether", "world_the_end"));

            bukkit.when(() -> Bukkit.getWorld(worldName)).thenReturn(null);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(worldFolder.getParentFile());
            Query<WorldSettings> mockQuery = stubQueryChain();

            assertThat(worldFolder).exists();

            boolean result = worldService.deleteWorld(worldName);

            assertThat(result).isTrue();
            assertThat(worldFolder).doesNotExist();
            verify(mockQuery).delete();
        } finally {
            deleteRecursively(worldFolder);
        }
    }
}
