package com.ultikits.plugins.worlds.service;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;
import com.ultikits.plugins.worlds.config.WorldConfig;
import com.ultikits.plugins.worlds.entity.WorldSettings;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.Query;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
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
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Checks the operator procedures published in `CHANGELOG.md` against what this code actually does.
 *
 * <p>Gate-1 R4-WR-11. The console line reports an observation and points at the changelog for the
 * procedure; that delegation is only worth anything if the text it delegates to is true. It was not:
 * the entry told an operator with a single-player save layout that moving one dimension directory
 * out reaches the "answered" case, when a single-player save also carries the overworld's top-level
 * {@code region} directory and therefore lands in the ambiguous case instead. A document that the
 * code delegates to needs the same per-case verification the code's own branches get, and prose
 * cannot supply it.
 *
 * <p>So each test below builds the folder as the changelog says it will look <em>after</em> the
 * operator does what the changelog tells them to do, and asserts the outcome the changelog promises.
 * A future edit to either side that breaks the agreement fails here.
 *
 * <p>These are characterisation tests over behaviour that was already correct -- the defect was in
 * the prose, not in the branch order -- so they are not paired with a red-before-fix. What makes
 * them bite is the mutation {@code 22-check-region-before-symlink}, which reorders the rules the
 * documented procedures depend on.
 *
 * @author wisdomme
 * @version 2.0.0
 */
@DisplayName("Published folder-shape procedures agree with the code (UltiWorlds#22, gate-1 R4-WR-11)")
class WorldServiceFolderShapeProcedureTest {

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
     * Loads a world whose folder holds exactly {@code entries} and returns the environment the
     * service handed to the server, or {@code null} when it handed none -- which is the shape of
     * "no environment was applied", the outcome the changelog calls the server's own default.
     */
    @SuppressWarnings("deprecation")
    private World.Environment environmentAppliedFor(String worldName, String... entries)
            throws IOException {
        File container = Files.createTempDirectory("p17w1shape").toFile();
        try {
            File worldFolder = new File(container, worldName);
            assertThat(worldFolder.mkdirs()).isTrue();
            for (String entry : entries) {
                assertThat(new File(worldFolder, entry).mkdirs()).isTrue();
            }
            return environmentAppliedIn(container, worldName);
        } finally {
            deleteRecursively(container);
        }
    }

    @SuppressWarnings("deprecation")
    private World.Environment environmentAppliedIn(File container, String worldName) {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            org.bukkit.UnsafeValues unsafe = mock(org.bukkit.UnsafeValues.class);
            when(unsafe.getMainLevelName()).thenReturn("world");
            bukkit.when(Bukkit::getUnsafe).thenReturn(unsafe);
            bukkit.when(() -> Bukkit.getWorld(worldName)).thenReturn(null);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
            stubQueryChain();

            AtomicReference<WorldCreator> captured = new AtomicReference<WorldCreator>();
            bukkit.when(() -> Bukkit.createWorld(any(WorldCreator.class))).thenAnswer(invocation -> {
                WorldCreator creator = invocation.getArgument(0);
                captured.set(creator);
                World world = mock(World.class);
                when(world.getEnvironment()).thenReturn(creator.environment());
                when(world.getPlayers()).thenReturn(Collections.<org.bukkit.entity.Player>emptyList());
                return world;
            });

            assertThat(worldService.loadWorld(worldName)).isTrue();
            // A creator that was never told an environment still reports NORMAL, because that is
            // WorldCreator's own default -- so "no environment applied" is read as NORMAL here, and
            // every assertion below that expects NORMAL is an assertion that nothing was applied.
            return captured.get().environment();
        }
    }

    // --- shape 1: a dimension directory and no top-level region -------------------------------

    @Test
    @DisplayName("shape 1 is answered, and the published action on it reaches shape 5")
    void shapeOneIsAnsweredAndItsActionReachesShapeFive() throws IOException {
        // As published: "an ordinary nether (or end) world. The environment is applied."
        assertThat(environmentAppliedFor("s1neth", "DIM-1")).isEqualTo(World.Environment.NETHER);
        assertThat(environmentAppliedFor("s1end", "DIM1")).isEqualTo(World.Environment.THE_END);
        // As published: "move that directory to a path outside the world folder; the world then
        // loads with the server's default" -- i.e. the folder becomes shape 5.
        assertThat(environmentAppliedFor("s1gone")).isEqualTo(World.Environment.NORMAL);
    }

    // --- shape 2: a dimension directory AND a top-level region --------------------------------

    @Test
    @DisplayName("shape 2 applies nothing, and both published actions on it land where it says")
    void shapeTwoAppliesNothingAndBothActionsLandWhereItSays() throws IOException {
        assertThat(environmentAppliedFor("s2both", "DIM-1", "region"))
                .isEqualTo(World.Environment.NORMAL);
        // As published: "Move the `region` directory out and you are in the first case" -- answered.
        assertThat(environmentAppliedFor("s2kept", "DIM-1")).isEqualTo(World.Environment.NETHER);
        // "...move the dimension directory out and you are in the last one" -- nothing applied.
        assertThat(environmentAppliedFor("s2drop", "region")).isEqualTo(World.Environment.NORMAL);
    }

    // --- shape 3: both DIM-1 and DIM1 ---------------------------------------------------------

    @Test
    @DisplayName("shape 3's published action lands in shape 2 for a single-player save, and in shape 1 without a region")
    void shapeThreeLandsWhereTheFolderContentsDecide() throws IOException {
        // A single-player save: all three dimensions share one folder, so `region` is present.
        assertThat(environmentAppliedFor("s3save", "DIM-1", "DIM1", "region"))
                .isEqualTo(World.Environment.NORMAL);
        // As published: "a single-player save also has the overworld's top-level `region`
        // directory, so you land in the second case above and have to choose there too."
        // THIS is the assertion the old text failed: it promised the first case, which is answered.
        assertThat(environmentAppliedFor("s3saveMoved", "DIM-1", "region"))
                .isEqualTo(World.Environment.NORMAL);
        // As published: "a folder with no top-level `region` lands in the first case and is
        // answered."
        assertThat(environmentAppliedFor("s3bare", "DIM-1", "DIM1"))
                .isEqualTo(World.Environment.NORMAL);
        assertThat(environmentAppliedFor("s3bareMoved", "DIM-1"))
                .isEqualTo(World.Environment.NETHER);
    }

    // --- shape 4: a symlinked dimension entry --------------------------------------------------

    @Test
    @DisplayName("shape 4 is checked before the other ambiguous cases, so moving directories cannot help")
    void shapeFourIsCheckedFirstSoMovingDirectoriesCannotHelp() throws IOException {
        File container = Files.createTempDirectory("p17w1shape").toFile();
        try {
            File target = new File(container, "elsewhere");
            assertThat(target.mkdirs()).isTrue();

            // A symlinked DIM-1 beside a top-level region: BOTH the symlink rule and the region
            // rule match. As published: "This is checked before the other two ambiguous cases, so
            // moving directories out cannot help while the link is still a link."
            File linked = new File(container, "s4link");
            assertThat(linked.mkdirs()).isTrue();
            assertThat(new File(linked, "region").mkdirs()).isTrue();
            Files.createSymbolicLink(new File(linked, "DIM-1").toPath(), target.toPath());
            assertThat(environmentAppliedIn(container, "s4link")).isEqualTo(World.Environment.NORMAL);
            // Both rules match this folder and both refuse, so the ORDER is observable only in
            // which observation is reported. The published procedure for this shape rests on the
            // symlink rule winning -- it is why "moving directories out cannot help" is true --
            // so that is what has to be asserted, not merely the (identical) outcome.
            PluginLogger logger = UltiWorldsTestHelper.getMockLogger();
            verify(logger, atLeastOnce()).warn(contains("symbolic link"));
            verify(logger, never()).warn(contains("each holding a different world's terrain"));

            // Remove the other directory -- still nothing applied, because the link is still a link.
            assertThat(new File(linked, "region").delete()).isTrue();
            assertThat(environmentAppliedIn(container, "s4link")).isEqualTo(World.Environment.NORMAL);

            // As published: "replace the link with a real directory -- after which the folder is
            // read as one of the cases above, whichever one it then matches."
            File replaced = new File(container, "s4real");
            assertThat(replaced.mkdirs()).isTrue();
            assertThat(new File(replaced, "DIM-1").mkdirs()).isTrue();
            assertThat(environmentAppliedIn(container, "s4real")).isEqualTo(World.Environment.NETHER);
        } finally {
            deleteRecursively(container);
        }
    }

    // --- shape 5: no dimension directory -------------------------------------------------------

    @Test
    @DisplayName("shape 5 applies nothing and changes nothing, whatever else the folder holds")
    void shapeFiveAppliesNothing() throws IOException {
        assertThat(environmentAppliedFor("s5plain", "region", "entities", "data"))
                .isEqualTo(World.Environment.NORMAL);
        assertThat(environmentAppliedFor("s5empty")).isEqualTo(World.Environment.NORMAL);
    }
}
