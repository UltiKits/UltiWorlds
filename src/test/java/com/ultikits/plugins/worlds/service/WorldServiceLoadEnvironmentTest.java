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
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Regression guard for {@code UltiKits/UltiWorlds#22}: {@code /world load} rebuilt an unloaded
 * world with a bare {@code new WorldCreator(name)}, and {@link WorldCreator} defaults to
 * {@link World.Environment#NORMAL}, so a NETHER or THE_END world came back as an overworld and new
 * terrain generated over the stored one.
 *
 * <p>The environment is resolved from two independent sources, and both are exercised here:
 * <ol>
 *   <li>what the service recorded while the world was created, loaded or unloaded in this session;</li>
 *   <li>failing that, the dimension sub-folder Bukkit itself writes inside the world folder --
 *       {@code DIM-1} for NETHER, {@code DIM1} for THE_END. Measured on this project's own Paper
 *       test servers: across 19 world folders, all 5 NETHER worlds carry a top-level {@code DIM-1}
 *       and no top-level {@code region}, all 5 THE_END worlds carry {@code DIM1}, and all 9 others
 *       (including a freshly created world that had never saved a chunk) carry neither.</li>
 * </ol>
 *
 * <p>Every assertion reads {@link WorldCreator#environment()} off the creator the service actually
 * handed to {@code Bukkit.createWorld}, which is the value that decides the dimension -- not a
 * value the test itself supplied.
 *
 * @author wisdomme
 * @version 2.0.0
 */
@DisplayName("WorldService load environment (UltiWorlds#22)")
class WorldServiceLoadEnvironmentTest {

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
    private void stubQueryChain() {
        Query<WorldSettings> mockQuery = mock(Query.class);
        when(mockDataOperator.query()).thenReturn(mockQuery);
        when(mockQuery.where(anyString())).thenReturn(mockQuery);
        when(mockQuery.eq(any())).thenReturn(mockQuery);
        when(mockQuery.first()).thenReturn(null);
        when(mockQuery.delete()).thenReturn(0);
    }

    /** A throwaway world container holding one world folder with a fixed, pattern-legal name. */
    private File newContainer() throws IOException {
        return Files.createTempDirectory("p17w1env").toFile();
    }

    private File newWorldFolder(File container, String name, String... dimensionFolders) {
        File worldFolder = new File(container, name);
        assertThat(worldFolder.mkdirs()).isTrue();
        for (String dimensionFolder : dimensionFolders) {
            assertThat(new File(worldFolder, dimensionFolder).mkdirs()).isTrue();
        }
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

    private World mockWorld(World.Environment environment) {
        World world = mock(World.class);
        when(world.getEnvironment()).thenReturn(environment);
        when(world.getPlayers()).thenReturn(Collections.<Player>emptyList());
        return world;
    }

    /**
     * Stubs {@code Bukkit.createWorld}, recording the creator the service passed in and returning
     * a world reporting whatever environment that creator asked for -- the same relationship a real
     * server has, so a test cannot "restore" an environment the service never requested.
     */
    @SuppressWarnings("deprecation")
    private AtomicReference<WorldCreator> captureCreator(MockedStatic<Bukkit> bukkit) {
        // `new WorldCreator(name)` derives its NamespacedKey from Bukkit.getUnsafe()
        // .getMainLevelName(), which the static mock would otherwise return null for, so the
        // constructor would throw before the service ever set an environment.
        org.bukkit.UnsafeValues unsafe = mock(org.bukkit.UnsafeValues.class);
        when(unsafe.getMainLevelName()).thenReturn("world");
        bukkit.when(Bukkit::getUnsafe).thenReturn(unsafe);

        AtomicReference<WorldCreator> captured = new AtomicReference<WorldCreator>();
        bukkit.when(() -> Bukkit.createWorld(any(WorldCreator.class))).thenAnswer(invocation -> {
            WorldCreator creator = invocation.getArgument(0);
            captured.set(creator);
            return mockWorld(creator.environment());
        });
        return captured;
    }

    @Test
    @DisplayName("loadWorld restores the NETHER environment recorded when the world was unloaded")
    void loadRestoresTheEnvironmentRecordedAtUnload() throws IOException {
        File container = newContainer();
        // No dimension folder at all, so only the recorded value can supply NETHER here.
        newWorldFolder(container, "netherw");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            AtomicReference<World> live = new AtomicReference<World>(mockWorld(World.Environment.NETHER));
            World defaultWorld = mockWorld(World.Environment.NORMAL);
            when(defaultWorld.getSpawnLocation()).thenReturn(mock(Location.class));

            bukkit.when(() -> Bukkit.getWorld("netherw")).thenAnswer(invocation -> live.get());
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(defaultWorld);
            bukkit.when(() -> Bukkit.unloadWorld(any(World.class), any(Boolean.class))).thenReturn(true);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
            when(mockConfig.getDefaultWorld()).thenReturn("world");
            stubQueryChain();
            AtomicReference<WorldCreator> captured = captureCreator(bukkit);

            assertThat(worldService.unloadWorld("netherw", true)).isTrue();
            live.set(null);

            assertThat(worldService.loadWorld("netherw")).isTrue();

            assertThat(captured.get()).isNotNull();
            assertThat(captured.get().environment()).isEqualTo(World.Environment.NETHER);
        } finally {
            deleteRecursively(container);
        }
    }

    @Test
    @DisplayName("loadWorld restores the THE_END environment recorded when the world was unloaded")
    void loadRestoresTheEndEnvironmentRecordedAtUnload() throws IOException {
        File container = newContainer();
        newWorldFolder(container, "endw");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            AtomicReference<World> live = new AtomicReference<World>(mockWorld(World.Environment.THE_END));
            World defaultWorld = mockWorld(World.Environment.NORMAL);
            when(defaultWorld.getSpawnLocation()).thenReturn(mock(Location.class));

            bukkit.when(() -> Bukkit.getWorld("endw")).thenAnswer(invocation -> live.get());
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(defaultWorld);
            bukkit.when(() -> Bukkit.unloadWorld(any(World.class), any(Boolean.class))).thenReturn(true);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
            when(mockConfig.getDefaultWorld()).thenReturn("world");
            stubQueryChain();
            AtomicReference<WorldCreator> captured = captureCreator(bukkit);

            assertThat(worldService.unloadWorld("endw", true)).isTrue();
            live.set(null);

            assertThat(worldService.loadWorld("endw")).isTrue();

            assertThat(captured.get().environment()).isEqualTo(World.Environment.THE_END);
        } finally {
            deleteRecursively(container);
        }
    }

    @Test
    @DisplayName("loadWorld restores the environment a world was created with, across an unload")
    void loadRestoresTheEnvironmentRecordedAtCreation() throws IOException {
        File container = newContainer();
        File worldFolder = new File(container, "madew");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            AtomicReference<World> live = new AtomicReference<World>(null);
            bukkit.when(() -> Bukkit.getWorld("madew")).thenAnswer(invocation -> live.get());
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
            stubQueryChain();
            AtomicReference<WorldCreator> captured = captureCreator(bukkit);

            assertThat(worldService.createWorld("madew", World.Environment.NETHER, WorldType.NORMAL, null))
                    .isTrue();
            assertThat(captured.get().environment()).isEqualTo(World.Environment.NETHER);

            // The server writes the folder as part of creating the world; the fixture stands in
            // for that, WITHOUT a dimension folder, so only the recorded value can supply NETHER.
            assertThat(worldFolder.mkdirs()).isTrue();
            captured.set(null);

            assertThat(worldService.loadWorld("madew")).isTrue();

            assertThat(captured.get()).isNotNull();
            assertThat(captured.get().environment()).isEqualTo(World.Environment.NETHER);
        } finally {
            deleteRecursively(container);
        }
    }

    @Test
    @DisplayName("loadWorld infers NETHER from the DIM-1 folder when nothing was recorded")
    void loadInfersNetherFromTheDimensionFolder() throws IOException {
        File container = newContainer();
        newWorldFolder(container, "diskneth", "DIM-1");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("diskneth")).thenReturn(null);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
            stubQueryChain();
            AtomicReference<WorldCreator> captured = captureCreator(bukkit);

            assertThat(worldService.loadWorld("diskneth")).isTrue();

            assertThat(captured.get().environment()).isEqualTo(World.Environment.NETHER);
        } finally {
            deleteRecursively(container);
        }
    }

    @Test
    @DisplayName("loadWorld infers THE_END from the DIM1 folder when nothing was recorded")
    void loadInfersTheEndFromTheDimensionFolder() throws IOException {
        File container = newContainer();
        newWorldFolder(container, "diskend", "DIM1");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("diskend")).thenReturn(null);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
            stubQueryChain();
            AtomicReference<WorldCreator> captured = captureCreator(bukkit);

            assertThat(worldService.loadWorld("diskend")).isTrue();

            assertThat(captured.get().environment()).isEqualTo(World.Environment.THE_END);
        } finally {
            deleteRecursively(container);
        }
    }

    @Test
    @DisplayName("loadWorld infers NORMAL for an overworld folder, which carries region/ and no dimension folder")
    void loadInfersNormalForAnOverworldFolder() throws IOException {
        File container = newContainer();
        newWorldFolder(container, "disknorm", "region", "entities", "data");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("disknorm")).thenReturn(null);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
            stubQueryChain();
            AtomicReference<WorldCreator> captured = captureCreator(bukkit);

            assertThat(worldService.loadWorld("disknorm")).isTrue();

            assertThat(captured.get().environment()).isEqualTo(World.Environment.NORMAL);
        } finally {
            deleteRecursively(container);
        }
    }

    @Test
    @DisplayName("loadWorld reads only top-level dimension folders, not one nested inside the world's own data")
    void loadIgnoresADimensionNameNestedInsideTheWorldFolder() throws IOException {
        File container = newContainer();
        // Top level says THE_END; a folder NAMED like the nether dimension sits one level down,
        // where a recursive or careless check would find it. If the nested name won, this would
        // come back NETHER; if no inference ran at all, it would come back NORMAL. Only reading
        // the top level alone yields THE_END, so this single assertion separates all three.
        File worldFolder = newWorldFolder(container, "nestedw", "DIM1");
        assertThat(new File(new File(worldFolder, "data"), "DIM-1").mkdirs()).isTrue();

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("nestedw")).thenReturn(null);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
            stubQueryChain();
            AtomicReference<WorldCreator> captured = captureCreator(bukkit);

            assertThat(worldService.loadWorld("nestedw")).isTrue();

            assertThat(captured.get().environment()).isEqualTo(World.Environment.THE_END);
        } finally {
            deleteRecursively(container);
        }
    }

    @Test
    @DisplayName("deleteWorld forgets the recorded environment, so a later world of the same name is not mislabelled")
    void deleteForgetsTheRecordedEnvironment() throws IOException {
        File container = newContainer();
        File worldFolder = newWorldFolder(container, "reusedw");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            AtomicReference<World> live = new AtomicReference<World>(mockWorld(World.Environment.NETHER));
            World defaultWorld = mockWorld(World.Environment.NORMAL);
            when(defaultWorld.getSpawnLocation()).thenReturn(mock(Location.class));

            bukkit.when(() -> Bukkit.getWorld("reusedw")).thenAnswer(invocation -> live.get());
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(defaultWorld);
            bukkit.when(() -> Bukkit.unloadWorld(any(World.class), any(Boolean.class))).thenReturn(true);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
            when(mockConfig.getDefaultWorld()).thenReturn("world");
            when(mockConfig.getProtectedWorlds()).thenReturn(Collections.<String>emptyList());
            stubQueryChain();
            AtomicReference<WorldCreator> captured = captureCreator(bukkit);

            assertThat(worldService.unloadWorld("reusedw", true)).isTrue();
            live.set(null);
            assertThat(worldService.deleteWorld("reusedw")).isTrue();
            assertThat(worldFolder).doesNotExist();

            // An operator (or another tool) puts a plain overworld back under the same name.
            assertThat(worldFolder.mkdirs()).isTrue();
            assertThat(new File(worldFolder, "region").mkdirs()).isTrue();

            assertThat(worldService.loadWorld("reusedw")).isTrue();

            assertThat(captured.get().environment()).isEqualTo(World.Environment.NORMAL);
        } finally {
            deleteRecursively(container);
        }
    }
}
