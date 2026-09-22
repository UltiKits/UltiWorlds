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
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
     * The exact sentence that closes every line this module prints about a world's environment.
     * Stated here independently of production: the pin is that the two agree.
     */
    private static final String POINTER =
            " What each of these folder shapes means, and what can be done about it, is in this"
                    + " module's CHANGELOG.md changelog entry for this version, and in"
                    + " UltiKits/UltiWorlds#22.";

    /**
     * The one line the logger was given, asserted to be exactly one.
     *
     * <p>Gate-1 R5-WR-17. What replaced this was a blacklist -- a list of phrasings the line must
     * not contain -- and a blacklist constrains only what its author thought of. Measured, the
     * previous list was evadable by a double space, by capitalising and dropping an article, and
     * by omitting the verb it keyed on; and because it listed the NEGATED form "do not delete", it
     * did not catch an affirmative "delete", which is the round-1 defect it was written to pin.
     *
     * <p>So the assertions below state what the line must BE. Anything appended, reworded or
     * inserted fails, whatever it says, because it is not the expected form -- and that covers the
     * phrasings nobody has thought of yet, which is the half a blacklist can never reach.
     */
    private String onlyLineLoggedBy(PluginLogger logger) {
        ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
        verify(logger, atLeastOnce()).warn(lines.capture());
        assertThat(lines.getAllValues())
                .as("the module must print exactly one line about a world's environment")
                .hasSize(1);
        // Gate-1 R6-WR-23. Asserting the content of the warn(String) line leaves the other doors on
        // the same logger open: the round-1 destructive remedy passed verbatim through
        // getLogger().info(...), and a remedy through the warn(String, Object...) varargs overload
        // passed too -- both NO-RED against the whole-line assertions, because neither is a
        // warn(String). One line closes every route at once, including ones nobody has named.
        verifyNoMoreInteractions(logger);
        return lines.getAllValues().get(0);
    }

    /** The whole refusal line, for a branch whose own observation is {@code observation}. */
    private void assertRefusedWith(PluginLogger logger, String world, String observation) {
        assertThat(onlyLineLoggedBy(logger)).isEqualTo(
                "World '" + world + "': no environment was applied, because " + observation + "."
                        + " This module does not guess an environment it cannot read from the"
                        + " folder, so the world was loaded with the server's own default"
                        + " environment -- the same as before this version." + POINTER);
    }

    /** The whole answering line. */
    private void assertAnsweredWith(PluginLogger logger, String world,
                                    World.Environment environment, String marker) {
        assertThat(onlyLineLoggedBy(logger)).isEqualTo(
                "World '" + world + "' was loaded as " + environment
                        + ", because its folder contains a top-level '" + marker + "' directory and"
                        + " no top-level 'region' directory." + POINTER);
    }

    private World mockWorld(World.Environment environment) {
        return mockWorld(environment, null);
    }

    /**
     * A live world that knows its own name. The name matters: the service records an environment
     * against the identity the server reports for a loaded world, not against the spelling the
     * caller happened to type, so a fixture whose `getName()` is unstubbed is not a live world --
     * it is a world with no identity, and it made every such test error rather than fail.
     */
    private World mockWorld(World.Environment environment, String name) {
        World world = mock(World.class);
        when(world.getEnvironment()).thenReturn(environment);
        when(world.getPlayers()).thenReturn(Collections.<Player>emptyList());
        if (name != null) {
            when(world.getName()).thenReturn(name);
        }
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
    @DisplayName("loadWorld puts a NETHER world back as NETHER across an unload")
    void loadRestoresNetherAcrossAnUnload() throws IOException {
        File container = newContainer();
        // The marker a real server writes for a nether world: every dimension stores its data under
        // its own dimension path, so `DIM-1` exists from creation, before anyone enters the world.
        newWorldFolder(container, "netherw", "DIM-1");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            AtomicReference<World> live = new AtomicReference<World>(mockWorld(World.Environment.NETHER, "netherw"));
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
    @DisplayName("loadWorld puts a THE_END world back as THE_END across an unload")
    void loadRestoresTheEndAcrossAnUnload() throws IOException {
        File container = newContainer();
        newWorldFolder(container, "endw", "DIM1");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            AtomicReference<World> live = new AtomicReference<World>(mockWorld(World.Environment.THE_END, "endw"));
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
    @DisplayName("a world created as NETHER comes back as NETHER after an unload")
    void loadRestoresTheEnvironmentAWorldWasCreatedWith() throws IOException {
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

            // The server writes the folder as part of creating the world, including the
            // dimension path each dimension stores its data under; the fixture stands in for that.
            assertThat(new File(worldFolder, "DIM-1").mkdirs()).isTrue();
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
            // ...and nothing through any other method on the same logger either.
            verifyNoMoreInteractions(UltiWorldsTestHelper.getMockLogger());
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
    @DisplayName("repairing an ambiguous folder works in the same session: the folder is read again")
    void theRepairedFolderIsReadAgainInTheSameSession() throws IOException {
        File container = newContainer();
        // Ambiguous: the shape the changelog tells an operator to repair by moving `region` out.
        File worldFolder = newWorldFolder(container, "repairw", "DIM-1", "region");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("repairw")).thenReturn(null);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
            stubQueryChain();
            AtomicReference<WorldCreator> captured = captureCreator(bukkit);

            assertThat(worldService.loadWorld("repairw")).isTrue();
            assertThat(captured.get().environment()).isEqualTo(World.Environment.NORMAL);

            // The operator now does exactly what the published procedure says, and reloads --
            // without restarting the server, which the procedure does not ask them to do.
            assertThat(new File(worldFolder, "region").delete()).isTrue();
            captured.set(null);

            assertThat(worldService.loadWorld("repairw")).isTrue();

            // Before this was fixed, the NORMAL the server defaulted to during the AMBIGUOUS load
            // had been recorded as though the module had decided it, so the repaired folder was
            // never looked at again and the world came back NORMAL a second time. A value the
            // module did not choose must not become the answer to the next question.
            assertThat(captured.get().environment()).isEqualTo(World.Environment.NETHER);
        } finally {
            deleteRecursively(container);
        }
    }

    @Test
    @DisplayName("an unload in between does not change what the repaired folder says")
    void anUnloadInBetweenDoesNotChangeWhatTheRepairedFolderSays() throws IOException {
        File container = newContainer();
        File worldFolder = newWorldFolder(container, "repairu", "DIM-1", "region");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            AtomicReference<World> live = new AtomicReference<World>(null);
            World defaultWorld = mockWorld(World.Environment.NORMAL);
            when(defaultWorld.getSpawnLocation()).thenReturn(mock(Location.class));
            bukkit.when(() -> Bukkit.getWorld("repairu")).thenAnswer(invocation -> live.get());
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(defaultWorld);
            bukkit.when(() -> Bukkit.unloadWorld(any(World.class), any(Boolean.class))).thenReturn(true);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
            when(mockConfig.getDefaultWorld()).thenReturn("world");
            stubQueryChain();
            AtomicReference<WorldCreator> captured = captureCreator(bukkit);

            assertThat(worldService.loadWorld("repairu")).isTrue();
            live.set(mockWorld(World.Environment.NORMAL));
            assertThat(worldService.unloadWorld("repairu", true)).isTrue();
            live.set(null);

            assertThat(new File(worldFolder, "region").delete()).isTrue();
            captured.set(null);

            assertThat(worldService.loadWorld("repairu")).isTrue();
            assertThat(captured.get().environment()).isEqualTo(World.Environment.NETHER);
        } finally {
            deleteRecursively(container);
        }
    }

    @Test
    @DisplayName("a second load while the world is loaded cannot change what a later load decides")
    void aSecondLoadWhileLoadedCannotChangeWhatALaterLoadDecides() throws IOException {
        File container = newContainer();
        // Ambiguous, so the first load declines and the server applies its own default.
        File worldFolder = newWorldFolder(container, "fastpathw", "DIM-1", "region");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            AtomicReference<World> live = new AtomicReference<World>(null);
            World defaultWorld = mockWorld(World.Environment.NORMAL);
            when(defaultWorld.getSpawnLocation()).thenReturn(mock(Location.class));
            bukkit.when(() -> Bukkit.getWorld("fastpathw")).thenAnswer(invocation -> live.get());
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(defaultWorld);
            bukkit.when(() -> Bukkit.unloadWorld(any(World.class), any(Boolean.class))).thenReturn(true);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
            when(mockConfig.getDefaultWorld()).thenReturn("world");
            stubQueryChain();
            AtomicReference<WorldCreator> captured = captureCreator(bukkit);

            assertThat(worldService.loadWorld("fastpathw")).isTrue();
            assertThat(captured.get().environment()).isEqualTo(World.Environment.NORMAL);
            live.set(mockWorld(World.Environment.NORMAL, "fastpathw"));

            // The operator types the command a second time while the world is still loaded -- the
            // ordinary "is it up yet?" reflex, and a no-op as far as they can tell.
            assertThat(worldService.loadWorld("fastpathw")).isTrue();

            // They then repair the folder exactly as the published procedure says, and cycle it.
            assertThat(new File(worldFolder, "region").delete()).isTrue();
            assertThat(worldService.unloadWorld("fastpathw", true)).isTrue();
            live.set(null);
            captured.set(null);

            assertThat(worldService.loadWorld("fastpathw")).isTrue();

            // Anything carried between calls makes that harmless second command consequential: it
            // is the step that captures the server's own default as though this module had chosen
            // it, and the repaired folder is then never read. Reading the folder at the point of
            // use has no state for a repeated command to disturb.
            assertThat(captured.get().environment()).isEqualTo(World.Environment.NETHER);
        } finally {
            deleteRecursively(container);
        }
    }

    @Test
    @DisplayName("a folder that becomes ambiguous is not answered from memory of when it was not")
    void aFolderThatBecomesAmbiguousIsNotAnsweredFromMemory() throws IOException {
        File container = newContainer();
        File worldFolder = newWorldFolder(container, "driftw", "DIM-1");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            AtomicReference<World> live = new AtomicReference<World>(null);
            World defaultWorld = mockWorld(World.Environment.NORMAL);
            when(defaultWorld.getSpawnLocation()).thenReturn(mock(Location.class));
            bukkit.when(() -> Bukkit.getWorld("driftw")).thenAnswer(invocation -> live.get());
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(defaultWorld);
            bukkit.when(() -> Bukkit.unloadWorld(any(World.class), any(Boolean.class))).thenReturn(true);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
            when(mockConfig.getDefaultWorld()).thenReturn("world");
            stubQueryChain();
            AtomicReference<WorldCreator> captured = captureCreator(bukkit);

            assertThat(worldService.loadWorld("driftw")).isTrue();
            assertThat(captured.get().environment()).isEqualTo(World.Environment.NETHER);
            live.set(mockWorld(World.Environment.NETHER, "driftw"));
            assertThat(worldService.unloadWorld("driftw", true)).isTrue();
            live.set(null);

            // Someone restores a backup, or copies a save in, and the folder is now ambiguous.
            assertThat(new File(worldFolder, "region").mkdirs()).isTrue();
            captured.set(null);

            assertThat(worldService.loadWorld("driftw")).isTrue();

            // The repair tests above pin the direction where the folder gets better. This is the
            // direction where it gets worse, and it is the same rule: the folder is mutable
            // between two commands, so the answer has to be read when the question is asked. A
            // remembered NETHER here points the server at DIM-1/region while a top-level region/
            // sits beside it, which is precisely the ambiguity the module refuses to resolve.
            assertThat(captured.get().environment()).isEqualTo(World.Environment.NORMAL);
        } finally {
            deleteRecursively(container);
        }
    }


    // ---------------------------------------------------------------------------------------
    // Retired with the environment record (see CHANGELOG.md for this version). Two tests pinned
    // behaviour that only a remembered value could deliver, and are gone with it:
    //
    //   theRecordIsFoundWhateverCaseTheNameIsTypedIn -- a NETHER world whose folder carries NO
    //   dimension marker, unloaded and reloaded inside one session. Measured against real world
    //   folders, the server does not produce that shape: every dimension stores its data under its
    //   own dimension path, so a nether world has `DIM-1/data/` from creation. A world created and
    //   never entered (32K, no region/, no entities/, no poi/) still has its dimension data
    //   written. A marker-less folder that is genuinely not an overworld is an operator-made
    //   shape, and for operator-made shapes the repair tests above are the governing rule: the
    //   folder is read again, it is not answered from memory.
    //
    //   loadUsesARecordedEnvironmentSilently -- there is no silent answering path left to pin.
    //   Silence for an ordinary overworld folder is pinned by loadIsSilentForAnOrdinaryOverworldFolder;
    //   every other answer now comes from the folder and says so.
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("two case-differing folders that are genuinely different worlds each answer for themselves")
    void twoCaseDifferingFoldersEachAnswerForThemselves() throws IOException {
        File container = newContainer();
        // On a case-sensitive filesystem these are two different worlds. Bukkit will not load
        // both at once, but both folders exist, and the environment of one must never be applied
        // to the other. `Arena` is a nether world and says so; `arena` is an overworld and says so.
        // Giving `Arena` the marker is what keeps this test from passing vacuously -- with no
        // marker anywhere, every shape of this service answers NORMAL for `arena` regardless.
        newWorldFolder(container, "Arena", "DIM-1");
        newWorldFolder(container, "arena", "region");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            World liveArena = mockWorld(World.Environment.NETHER);
            when(liveArena.getName()).thenReturn("Arena");
            AtomicReference<World> live = new AtomicReference<World>(liveArena);
            World defaultWorld = mockWorld(World.Environment.NORMAL);
            when(defaultWorld.getSpawnLocation()).thenReturn(mock(Location.class));

            bukkit.when(() -> Bukkit.getWorld("Arena")).thenAnswer(invocation -> live.get());
            bukkit.when(() -> Bukkit.getWorld("arena")).thenReturn(null);
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(defaultWorld);
            bukkit.when(() -> Bukkit.unloadWorld(any(World.class), any(Boolean.class))).thenReturn(true);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
            when(mockConfig.getDefaultWorld()).thenReturn("world");
            stubQueryChain();
            AtomicReference<WorldCreator> captured = captureCreator(bukkit);

            assertThat(worldService.unloadWorld("Arena", true)).isTrue();
            live.set(null);

            assertThat(worldService.loadWorld("arena")).isTrue();

            // Anything that carries a world's environment from one call to the next, keyed by a
            // name, collapses these two into one and rebuilds `arena` -- an overworld -- as the
            // NETHER that belongs to `Arena`. Reading each world's own folder at the point of use
            // cannot make that mistake, because there is no key and nothing carried.
            assertThat(captured.get().environment()).isEqualTo(World.Environment.NORMAL);
        } finally {
            deleteRecursively(container);
        }
    }

    @Test
    @DisplayName("a later world of the same name is not mislabelled by the deleted one")
    void aLaterWorldOfTheSameNameIsNotMislabelled() throws IOException {
        File container = newContainer();
        File worldFolder = newWorldFolder(container, "reusedw", "DIM-1");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            AtomicReference<World> live = new AtomicReference<World>(mockWorld(World.Environment.NETHER, "reusedw"));
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

            // This branch's OWN observation, as the whole line and nothing else.
            assertRefusedWith(UltiWorldsTestHelper.getMockLogger(), "bothw",
                    "its folder contains a top-level 'DIM-1' directory and a top-level 'region'"
                            + " directory, each holding a different world's terrain");
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
            assertRefusedWith(UltiWorldsTestHelper.getMockLogger(), "savew",
                    "its folder contains both a top-level 'DIM-1' directory and a top-level 'DIM1'"
                            + " directory");
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
            // `linkw` contains nothing but the link, so a sentence promising an "other directory"
            // that "holds a real world's terrain" would describe something that does not exist.
            // The whole-line form rules that out without having to list it.
            assertRefusedWith(UltiWorldsTestHelper.getMockLogger(), "linkw",
                    "its dimension entry is a symbolic link, and this module does not follow links"
                            + " when reading a world folder, so whatever the link points at was not"
                            + " read");
        } finally {
            deleteRecursively(container);
        }
    }

    @Test
    @DisplayName("when a folder matches two refusal rules, the earlier rule is the one reported")
    void theEarlierRefusalRuleIsTheOneReported() throws IOException {
        File container = newContainer();
        File worldFolder = newWorldFolder(container, "orderw", "region");
        File target = new File(container, "elsewhere");
        assertThat(target.mkdirs()).isTrue();
        java.nio.file.Files.createSymbolicLink(
                new File(worldFolder, "DIM-1").toPath(), target.toPath());

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("orderw")).thenReturn(null);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
            stubQueryChain();
            captureCreator(bukkit);

            assertThat(worldService.loadWorld("orderw")).isTrue();

            // Both the symlink rule and the region rule match this folder and BOTH refuse, so the
            // environment is the same either way and only the reported observation can tell them
            // apart. The published procedure for the symlink shape rests on that rule winning --
            // it is why "moving directories out cannot help" is true -- so the order has to be
            // asserted on the line, not on the outcome. The outcome table cannot see it.
            assertRefusedWith(UltiWorldsTestHelper.getMockLogger(), "orderw",
                    "its dimension entry is a symbolic link, and this module does not follow links"
                            + " when reading a world folder, so whatever the link points at was not"
                            + " read");
        } finally {
            deleteRecursively(container);
        }
    }

    @Test
    @DisplayName("loadWorld reports a dangling dimension link instead of passing over it in silence")
    void loadReportsADanglingDimensionLink() throws IOException {
        File container = newContainer();
        File worldFolder = newWorldFolder(container, "danglw");
        // A nether world whose DIM-1 lives on a volume that is not mounted. The link is there; what
        // it points at is not. `File#isDirectory()` follows the link and answers false, so before
        // this was fixed the folder looked like an ordinary overworld: nothing inferred, NOTHING
        // LOGGED, and the world served as NORMAL -- UltiWorlds#22 happening silently, on the one
        // input where the module had no way of telling anybody.
        java.nio.file.Files.createSymbolicLink(
                new File(worldFolder, "DIM-1").toPath(),
                new File(container, "not-mounted").toPath());

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("danglw")).thenReturn(null);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
            stubQueryChain();
            AtomicReference<WorldCreator> captured = captureCreator(bukkit);

            // Pre-assertion: this really is the dangling case and not a missing entry.
            assertThat(java.nio.file.Files.isSymbolicLink(new File(worldFolder, "DIM-1").toPath()))
                    .isTrue();
            assertThat(new File(worldFolder, "DIM-1").isDirectory()).isFalse();

            assertThat(worldService.loadWorld("danglw")).isTrue();

            assertThat(captured.get().environment()).isEqualTo(World.Environment.NORMAL);
            assertRefusedWith(UltiWorldsTestHelper.getMockLogger(), "danglw",
                    "its 'DIM-1' entry is a symbolic link that does not lead to a directory, so the"
                            + " folder cannot be read as the world it may belong to");
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

            assertAnsweredWith(UltiWorldsTestHelper.getMockLogger(), "loudw",
                    World.Environment.NETHER, "DIM-1");
        } finally {
            deleteRecursively(container);
        }
    }


    @Test
    @DisplayName("an environment the server cannot rebuild never reaches the creator (gate-1 IN-01)")
    void aCustomEnvironmentNeverReachesTheCreator() throws IOException {
        File container = newContainer();
        newWorldFolder(container, "customw", "DIM-1");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            AtomicReference<World> live = new AtomicReference<World>(mockWorld(World.Environment.CUSTOM, "customw"));
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

            // CraftServer#createWorld throws IllegalArgumentException on CUSTOM, so handing it
            // back would turn "loads with the wrong environment" into "throws out of the command".
            // The live world reports CUSTOM and its folder reports NETHER: the answer must come
            // from the folder, which can only ever name an environment the server can rebuild.
            assertThat(captured.get().environment()).isNotEqualTo(World.Environment.CUSTOM);
            assertThat(captured.get().environment()).isEqualTo(World.Environment.NETHER);
        } finally {
            deleteRecursively(container);
        }
    }
}
