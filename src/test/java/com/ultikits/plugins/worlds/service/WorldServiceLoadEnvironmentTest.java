package com.ultikits.plugins.worlds.service;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;
import com.ultikits.plugins.worlds.config.WorldConfig;
import com.ultikits.plugins.worlds.entity.WorldSettings;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.Query;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

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
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    /**
     * Every refusal says the same two things, whichever branch produced it: that no environment was
     * applied, and where the procedure is written down. Gate-1 R3-WR-09: the line must point at the
     * procedure rather than inline one, because one sentence cannot give a correct procedure for
     * three structurally different folder shapes -- the shared instruction it replaced was wrong on
     * two of the three.
     */
    private void assertNoDecisionReported(PluginLogger logger) {
        verify(logger).warn(contains("no environment was applied"));
        verify(logger).warn(contains("server's own default environment"));
        assertNoInstruction(logger);
    }

    /**
     * The log states what it observed; it never tells the operator what to do about it.
     *
     * <p>The forbidden half is matched case-insensitively, over every line the logger was given.
     * Mockito's {@code contains} is case-sensitive, and an instruction appended as a new sentence
     * starts with a capital -- a mutation that appended "Leave only that dimension's own
     * directory" walked straight past a {@code never().warn(contains("leave only"))} guard. A
     * guard that can be evaded by capitalising the first letter is not a guard.
     */
    private void assertNoInstruction(PluginLogger logger) {
        verify(logger).warn(contains("changelog entry"));
        verify(logger).warn(contains("UltiKits/UltiWorlds#22"));
        // Gate-1 R4-IN-18: the console line carries no version string, so "this version's
        // changelog entry" is not findable from the console alone. The delegation is only as
        // good as the pointer, so the pointer names the file.
        verify(logger).warn(contains("CHANGELOG.md"));

        ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
        verify(logger, atLeastOnce()).warn(lines.capture());
        for (String line : lines.getAllValues()) {
            assertThat(line.toLowerCase(Locale.ROOT))
                    .as("a line the module printed must not instruct: %s", line)
                    .doesNotContain("stop the server")
                    .doesNotContain("leave only")
                    .doesNotContain("do not delete")
                    .doesNotContain("move the")
                    .doesNotContain("move that")
                    .doesNotContain("you should")
                    .doesNotContain("please ");
        }
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
    @DisplayName("loadWorld says nothing at all for an ordinary overworld folder, and logs nothing")
    void loadIsSilentForAnOrdinaryOverworldFolder() throws IOException {
        File container = newContainer();
        newWorldFolder(container, "disknorm", "region", "entities", "data");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("disknorm")).thenReturn(null);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
            stubQueryChain();
            AtomicReference<WorldCreator> captured = captureCreator(bukkit);

            assertThat(worldService.loadWorld("disknorm")).isTrue();

            // No dimension marker means no evidence, so nothing is inferred and the server's own
            // default applies -- exactly what this module did before it restored environments.
            assertThat(captured.get().environment()).isEqualTo(World.Environment.NORMAL);
            // ...and it must be silent. This runs for every ordinary world on every boot; a
            // WARNING here would train operators to ignore the ones that matter.
            verify(UltiWorldsTestHelper.getMockLogger(), never()).warn(anyString());
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

    // ---------------------------------------------------------------------------------------
    // Gate-1 WR-01: the folder heuristic runs unattended at boot (init() loops over
    // load_worlds_on_start), and a wrong answer silently points the server at a different set of
    // region files, so everything players built in the other set stops existing for them. These
    // tests pin the rule that it must decline rather than pick whenever the folder is ambiguous,
    // and that it is never silent when it does answer.
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("loadWorld declines to infer when a dimension folder sits beside a top-level region folder")
    void loadDeclinesWhenADimensionFolderSitsBesideATopLevelRegionFolder() throws IOException {
        File container = newContainer();
        // The physical footprint of UltiWorlds#22 itself: nether data in DIM-1 from before the
        // defect, overworld data at the top level written after a reload turned it NORMAL. Such a
        // folder exists in this project's own evidence tree (1 of the 7 DIM-1 directories under
        // the test-server tree sits beside a top-level region/).
        newWorldFolder(container, "bothw", "DIM-1", "region");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("bothw")).thenReturn(null);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
            stubQueryChain();
            AtomicReference<WorldCreator> captured = captureCreator(bukkit);

            assertThat(worldService.loadWorld("bothw")).isTrue();

            // NOT NETHER: the folder cannot say which of the two worlds the operator wants, so the
            // server's own default applies -- the same outcome this module produced before it
            // restored environments at all, which is why declining is not a regression.
            assertThat(captured.get().environment()).isEqualTo(World.Environment.NORMAL);

            PluginLogger logger = UltiWorldsTestHelper.getMockLogger();
            // This branch's OWN observation: two directories, each holding a world's terrain.
            verify(logger).warn(contains("bothw"));
            verify(logger).warn(contains("top-level 'DIM-1' directory"));
            verify(logger).warn(contains("top-level 'region' directory"));
            verify(logger).warn(contains("each holding a different world's terrain"));
            assertNoDecisionReported(logger);
        } finally {
            deleteRecursively(container);
        }
    }

    @Test
    @DisplayName("loadWorld declines to infer for a single-player save layout carrying both dimension folders")
    void loadDeclinesForASingleplayerSaveLayout() throws IOException {
        File container = newContainer();
        newWorldFolder(container, "savew", "DIM-1", "DIM1");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("savew")).thenReturn(null);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
            stubQueryChain();
            AtomicReference<WorldCreator> captured = captureCreator(bukkit);

            assertThat(worldService.loadWorld("savew")).isTrue();

            assertThat(captured.get().environment()).isEqualTo(World.Environment.NORMAL);
            PluginLogger logger = UltiWorldsTestHelper.getMockLogger();
            verify(logger).warn(contains("savew"));
            verify(logger).warn(contains("both a top-level 'DIM-1' directory and a top-level 'DIM1' directory"));
            assertNoDecisionReported(logger);
        } finally {
            deleteRecursively(container);
        }
    }

    @Test
    @DisplayName("loadWorld declines to infer from a dimension entry that is a symbolic link")
    void loadDeclinesWhenTheDimensionEntryIsASymbolicLink() throws IOException {
        File container = newContainer();
        File worldFolder = newWorldFolder(container, "linkw");
        File realDimension = new File(container, "elsewhere");
        assertThat(realDimension.mkdirs()).isTrue();
        java.nio.file.Files.createSymbolicLink(
                new File(worldFolder, "DIM-1").toPath(), realDimension.toPath());

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("linkw")).thenReturn(null);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
            stubQueryChain();
            AtomicReference<WorldCreator> captured = captureCreator(bukkit);

            // Pre-assertion: the link really does read as a directory, so this test is exercising
            // the symlink rule and not merely a missing folder.
            assertThat(new File(worldFolder, "DIM-1").isDirectory()).isTrue();

            assertThat(worldService.loadWorld("linkw")).isTrue();

            // deleteFolder in this same class deliberately does not follow links; reading one as
            // evidence about this world would be a second, contradictory policy on links.
            assertThat(captured.get().environment()).isEqualTo(World.Environment.NORMAL);
            PluginLogger logger = UltiWorldsTestHelper.getMockLogger();
            verify(logger).warn(contains("linkw"));
            verify(logger).warn(contains("symbolic link"));
            verify(logger).warn(contains("whatever the link points at was not read"));
            // `linkw` contains nothing but the link, so any sentence promising an "other
            // directory" that "holds a real world's terrain" would be describing something that
            // does not exist. That is what an instruction shared across branches did.
            verify(logger, never()).warn(contains("OTHER directory"));
            assertNoDecisionReported(logger);
        } finally {
            deleteRecursively(container);
        }
    }

    @Test
    @DisplayName("loadWorld logs at WARNING what it inferred, from what, and what to do if it is wrong")
    void loadLogsWhatItInferredAndFromWhat() throws IOException {
        File container = newContainer();
        newWorldFolder(container, "loudw", "DIM-1");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("loudw")).thenReturn(null);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
            stubQueryChain();
            AtomicReference<WorldCreator> captured = captureCreator(bukkit);

            assertThat(worldService.loadWorld("loudw")).isTrue();
            assertThat(captured.get().environment()).isEqualTo(World.Environment.NETHER);

            PluginLogger logger = UltiWorldsTestHelper.getMockLogger();
            verify(logger).warn(contains("loudw"));
            verify(logger).warn(contains("NETHER"));
            verify(logger).warn(contains("DIM-1"));
            verify(logger).warn(contains("no top-level 'region' directory"));
            assertNoInstruction(logger);
        } finally {
            deleteRecursively(container);
        }
    }

    @Test
    @DisplayName("loadWorld uses a recorded environment silently, because a record is not a guess")
    void loadUsesARecordedEnvironmentSilently() throws IOException {
        File container = newContainer();
        newWorldFolder(container, "quietw");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            AtomicReference<World> live = new AtomicReference<World>(mockWorld(World.Environment.NETHER));
            World defaultWorld = mockWorld(World.Environment.NORMAL);
            when(defaultWorld.getSpawnLocation()).thenReturn(mock(Location.class));

            bukkit.when(() -> Bukkit.getWorld("quietw")).thenAnswer(invocation -> live.get());
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(defaultWorld);
            bukkit.when(() -> Bukkit.unloadWorld(any(World.class), any(Boolean.class))).thenReturn(true);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
            when(mockConfig.getDefaultWorld()).thenReturn("world");
            stubQueryChain();
            AtomicReference<WorldCreator> captured = captureCreator(bukkit);

            assertThat(worldService.unloadWorld("quietw", true)).isTrue();
            live.set(null);
            assertThat(worldService.loadWorld("quietw")).isTrue();

            assertThat(captured.get().environment()).isEqualTo(World.Environment.NETHER);
            verify(UltiWorldsTestHelper.getMockLogger(), never()).warn(anyString());
        } finally {
            deleteRecursively(container);
        }
    }

    @Test
    @DisplayName("an environment the server cannot rebuild is never recorded (gate-1 IN-01)")
    void aCustomEnvironmentIsNeverRecorded() throws IOException {
        File container = newContainer();
        newWorldFolder(container, "customw");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            AtomicReference<World> live = new AtomicReference<World>(mockWorld(World.Environment.CUSTOM));
            World defaultWorld = mockWorld(World.Environment.NORMAL);
            when(defaultWorld.getSpawnLocation()).thenReturn(mock(Location.class));

            bukkit.when(() -> Bukkit.getWorld("customw")).thenAnswer(invocation -> live.get());
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(defaultWorld);
            bukkit.when(() -> Bukkit.unloadWorld(any(World.class), any(Boolean.class))).thenReturn(true);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
            when(mockConfig.getDefaultWorld()).thenReturn("world");
            stubQueryChain();
            AtomicReference<WorldCreator> captured = captureCreator(bukkit);

            assertThat(worldService.unloadWorld("customw", true)).isTrue();
            live.set(null);
            assertThat(worldService.loadWorld("customw")).isTrue();

            // CraftServer#createWorld throws IllegalArgumentException on CUSTOM, so handing it back
            // would turn "loads with the wrong environment" into "throws out of the command".
            assertThat(captured.get().environment()).isNotEqualTo(World.Environment.CUSTOM);
            assertThat(captured.get().environment()).isEqualTo(World.Environment.NORMAL);
        } finally {
            deleteRecursively(container);
        }
    }
}
