package com.ultikits.plugins.worlds.service;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;
import com.ultikits.plugins.worlds.config.WorldConfig;
import com.ultikits.plugins.worlds.entity.WorldSettings;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.Query;

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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Runs the outcome table published in {@code CHANGELOG.md} against this code, by reading the file.
 *
 * <p>Gate-1 R5-WR-16. The class this replaces transcribed the changelog into Java and then tested
 * the transcription, so it constrained the code and not the document: three document-only
 * mutations -- shape 2's outcomes swapped, shape 4's ordering inverted, and the defect a previous
 * round had found reinstated verbatim -- each left it at five passing tests. The exact regression
 * one round had caught could be put back into the shipped document with the gate green, while the
 * class's own javadoc claimed "an edit to either side fails here".
 *
 * <p>So this reads `CHANGELOG.md`, parses the table, and drives every fixture from what the file
 * actually contains. Nothing about the table is hard-coded: not the number of rows, not the folder
 * entries, not the expected environments. Editing the table without editing the code fails here,
 * and editing the code without editing the table fails here, which is the property the name claims.
 *
 * <p>The table's own notation, also read from the file rather than assumed: {@code folder holds}
 * lists the world folder's top-level entries, {@code (link)} marking a symbolic link to a directory
 * and {@code (dangling)} one whose target is missing; {@code applied} is the environment handed to
 * the server, {@code none} meaning none was supplied and the server's own default stands;
 * {@code move X out ->} is what results after that entry is moved out of the folder.
 *
 * @author wisdomme
 * @version 2.0.0
 */
@DisplayName("The published outcome table is run against the code (UltiWorlds#22, gate-1 R5-WR-16)")
class WorldServiceFolderShapeProcedureTest {

    private static final Path CHANGELOG = Paths.get("CHANGELOG.md");
    private static final String HEADER = "| folder holds | applied | move X out -> |";

    private int fixtureCount;

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

    // ---------------------------------------------------------------------------------------
    // reading the document
    // ---------------------------------------------------------------------------------------

    /** One published row: the folder's entries, the environment applied, and the move outcomes. */
    private static final class PublishedRow {
        private final String source;
        private final List<String> entries;
        private final String applied;
        private final Map<String, String> moveOutcomes;

        PublishedRow(String source, List<String> entries, String applied,
                     Map<String, String> moveOutcomes) {
            this.source = source;
            this.entries = entries;
            this.applied = applied;
            this.moveOutcomes = moveOutcomes;
        }
    }

    private List<PublishedRow> readPublishedTable() throws IOException {
        assertThat(CHANGELOG)
                .as("the changelog this test reads must exist at the path it reads")
                .exists();
        List<String> lines = Files.readAllLines(CHANGELOG, StandardCharsets.UTF_8);

        int header = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().equals(HEADER)) {
                header = i;
                break;
            }
        }
        assertThat(header)
                .as("the published outcome table, found by its header line: %s", HEADER)
                .isNotEqualTo(-1);

        List<PublishedRow> rows = new ArrayList<PublishedRow>();
        for (int i = header + 2; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (!line.startsWith("|")) {
                break;
            }
            String[] cells = line.split("\\|", -1);
            assertThat(cells.length)
                    .as("a row of the published table has three cells: %s", line)
                    .isEqualTo(5);
            rows.add(new PublishedRow(line, splitEntries(cells[1]), cells[2].trim(),
                    parseMoveOutcomes(cells[3])));
        }

        // A parser that silently finds nothing would make every assertion below vacuous.
        assertThat(rows).as("rows parsed out of the published table").isNotEmpty();
        assertThat(rows).as("the table must exercise both dimensions and the no-answer case")
                .anyMatch(row -> "NETHER".equals(row.applied))
                .anyMatch(row -> "THE_END".equals(row.applied))
                .anyMatch(row -> "none".equals(row.applied));
        return rows;
    }

    private List<String> splitEntries(String cell) {
        List<String> entries = new ArrayList<String>();
        for (String piece : cell.split(",")) {
            String entry = piece.trim().replace("`", "");
            if (!entry.isEmpty()) {
                entries.add(entry);
            }
        }
        return entries;
    }

    private Map<String, String> parseMoveOutcomes(String cell) {
        Map<String, String> outcomes = new LinkedHashMap<String, String>();
        for (String piece : cell.split(";")) {
            String claim = piece.trim();
            if (claim.isEmpty()) {
                continue;
            }
            String[] halves = claim.split("->");
            assertThat(halves.length)
                    .as("a move outcome is written `entry` -> result: %s", claim)
                    .isEqualTo(2);
            outcomes.put(halves[0].trim().replace("`", ""), halves[1].trim());
        }
        return outcomes;
    }

    // ---------------------------------------------------------------------------------------
    // running it against the code
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("every published row's `applied` column is what the code applies")
    void everyPublishedAppliedColumnHolds() throws IOException {
        for (PublishedRow row : readPublishedTable()) {
            assertThat(environmentFor(row.entries))
                    .as("published row: %s", row.source)
                    .isEqualTo(expected(row.applied));
        }
    }

    @Test
    @DisplayName("every published `move X out` outcome is what the code does once X is gone")
    void everyPublishedMoveOutcomeHolds() throws IOException {
        int checked = 0;
        for (PublishedRow row : readPublishedTable()) {
            for (Map.Entry<String, String> outcome : row.moveOutcomes.entrySet()) {
                List<String> remaining = new ArrayList<String>(row.entries);
                boolean removed = remaining.removeIf(
                        entry -> baseName(entry).equals(baseName(outcome.getKey())));
                assertThat(removed)
                        .as("`%s` must be one of the entries the row lists: %s",
                                outcome.getKey(), row.source)
                        .isTrue();
                assertThat(environmentFor(remaining))
                        .as("published row: %s -- after moving `%s` out", row.source,
                                outcome.getKey())
                        .isEqualTo(expected(outcome.getValue()));
                checked++;
            }
        }
        // Control: a table whose move column had been emptied would pass the loop above by
        // iterating nothing.
        assertThat(checked).as("move outcomes actually checked").isGreaterThanOrEqualTo(8);
    }

    private static String baseName(String entry) {
        int paren = entry.indexOf('(');
        return paren < 0 ? entry : entry.substring(0, paren);
    }

    private World.Environment expected(String published) {
        if ("none".equals(published)) {
            // Nothing supplied: WorldCreator's own default stands, and that default is NORMAL.
            return World.Environment.NORMAL;
        }
        return World.Environment.valueOf(published);
    }

    /** Builds a world folder holding exactly {@code entries} and returns what the service applied. */
    @SuppressWarnings("deprecation")
    private World.Environment environmentFor(List<String> entries) throws IOException {
        File container = Files.createTempDirectory("p17w1table").toFile();
        try {
            // A fresh name per fixture. The service records the environment of every world it
            // resolves, so reusing one name would make the second row read the FIRST row's record
            // instead of its own folder -- and the table would then be checked against a cache.
            String worldName = "tablew" + fixtureCount++;
            File worldFolder = new File(container, worldName);
            assertThat(worldFolder.mkdirs()).isTrue();
            for (String entry : entries) {
                String name = baseName(entry);
                if (entry.endsWith("(link)")) {
                    File target = new File(container, "target-" + name);
                    assertThat(target.mkdirs()).isTrue();
                    Files.createSymbolicLink(new File(worldFolder, name).toPath(), target.toPath());
                } else if (entry.endsWith("(dangling)")) {
                    Files.createSymbolicLink(new File(worldFolder, name).toPath(),
                            new File(container, "gone-" + name).toPath());
                } else {
                    assertThat(new File(worldFolder, name).mkdirs()).isTrue();
                }
            }

            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                org.bukkit.UnsafeValues unsafe = mock(org.bukkit.UnsafeValues.class);
                when(unsafe.getMainLevelName()).thenReturn("world");
                bukkit.when(Bukkit::getUnsafe).thenReturn(unsafe);
                bukkit.when(() -> Bukkit.getWorld(worldName)).thenReturn(null);
                bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
                stubQueryChain();

                AtomicReference<WorldCreator> captured = new AtomicReference<WorldCreator>();
                bukkit.when(() -> Bukkit.createWorld(any(WorldCreator.class))).thenAnswer(call -> {
                    WorldCreator creator = call.getArgument(0);
                    captured.set(creator);
                    World world = mock(World.class);
                    when(world.getEnvironment()).thenReturn(creator.environment());
                    when(world.getPlayers())
                            .thenReturn(Collections.<org.bukkit.entity.Player>emptyList());
                    return world;
                });

                assertThat(worldService.loadWorld(worldName)).isTrue();
                return captured.get().environment();
            }
        } finally {
            deleteRecursively(container);
        }
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
        if (children != null && !Files.isSymbolicLink(file.toPath())) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}
