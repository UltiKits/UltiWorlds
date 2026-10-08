package com.ultikits.plugins.worlds.service;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;
import com.ultikits.plugins.worlds.commands.WorldCommand;
import com.ultikits.plugins.worlds.entity.WorldSettings;
import com.ultikits.plugins.worlds.testsupport.InterleavingDataOperator;
import com.ultikits.plugins.worlds.testsupport.SharedSqliteDatabase;
import com.ultikits.ultitools.entities.WhereCondition;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.impl.data.json.SimpleJsonDataOperator;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * A world-settings change when another server fills a column this server read as unset ({@code NULL}),
 * in the moment between this server's read and its write (UltiKits/UltiWorlds#55; maintainer decision
 * of 2026-10-06: UltiWorlds compares its {@code NULL} columns too, using the framework's
 * {@code DataOperator#updateIf} reading a {@code null} expected value as {@code IS NULL}).
 * <p>
 * The two window tests are the gate-1 review's probes {@code probeNullDifficultyWindow} and
 * {@code probeNullPostCmdWindow}, kept as tests: on the code before this change a column read as
 * {@code NULL} was not compared, so server B's value was written back as unset (difficulty) or lost
 * (B's post-teleport command). Real SQLite, two servers: each its own framework
 * {@code SQLiteDataOperator} on one file; server A's is wrapped so server B writes exactly between A's
 * read and A's write.
 * <p>
 * Controls: without another writer one attempt is made and the unset columns stay unset (on SQLite and
 * on the JSON backend), so comparing a column with {@code NULL} does not turn an ordinary change into a
 * contended one. A value server B wrote before server A's read is kept by the existing
 * {@code WorldSettingsCommandsSharedDatabaseTest#columnReadAsNullIsNotPutBack}.
 */
@DisplayName("A world-settings change keeps a value another server wrote into a column read as unset (UltiWorlds#55)")
class WorldSettingsNullColumnsSharedDatabaseTest {

    private static final String WORLD = "shared_world";

    @TempDir
    Path dir;

    private SharedSqliteDatabase database;
    private DataOperator<WorldSettings> serverB;
    private World world;

    @BeforeEach
    void setUp() throws Exception {
        UltiWorldsTestHelper.setUp();
        world = MockBukkit.getMock().addSimpleWorld(WORLD);
        database = SharedSqliteDatabase.in(dir);
        serverB = database.openAs(WorldSettings.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiWorldsTestHelper.tearDown();
    }

    private WorldCommand commandOver(WorldService service) throws Exception {
        WorldCommand command = new WorldCommand();
        UltiWorldsTestHelper.setField(command, "plugin", UltiWorldsTestHelper.getMockPlugin());
        UltiWorldsTestHelper.setField(command, "worldService", service);
        return command;
    }

    private WorldService serviceOver(DataOperator<WorldSettings> operator) throws Exception {
        WorldService service = new WorldService();
        UltiWorldsTestHelper.setField(service, "plugin", UltiWorldsTestHelper.getMockPlugin());
        UltiWorldsTestHelper.setField(service, "config", UltiWorldsTestHelper.createDefaultConfig());
        UltiWorldsTestHelper.setField(service, "dataOperator", operator);
        return service;
    }

    private Player operator() {
        Player player = UltiWorldsTestHelper.createMockPlayer("Admin", UUID.randomUUID());
        lenient().when(player.getWorld()).thenReturn(world);
        return player;
    }

    private static WorldSettings onlyRow(DataOperator<WorldSettings> table) {
        List<WorldSettings> rows = table.getAll(
                WhereCondition.builder().column("world_name").value(WORLD).build());
        assertThat(rows).as("exactly one stored settings row for the world").hasSize(1);
        return rows.get(0);
    }

    private WorldSettings storedRow() {
        return onlyRow(serverB);
    }

    /**
     * Server A's operator: right before A's first write, server B sets {@code column} to {@code value} on
     * the row whose id the test puts into {@code rowId} once A has created the row.
     */
    private InterleavingDataOperator<WorldSettings> withRowId(String column, Object value, String[] rowId) {
        return new InterleavingDataOperator<>(
                database.openAs(WorldSettings.class),
                () -> serverB.update(column, value, rowId[0]),
                1);
    }

    @Test
    @DisplayName("probeNullDifficultyWindow: server B sets the unset difficulty between server A's read and write; A's /world set description keeps it")
    void probeNullDifficultyWindow() throws Exception {
        String[] rowId = new String[1];
        InterleavingDataOperator<WorldSettings> operatorA = withRowId("difficulty", "HARD", rowId);
        WorldService serviceA = serviceOver(operatorA);
        serviceA.getOrCreateSettings(WORLD);
        WorldSettings before = storedRow();
        rowId[0] = before.getId();
        assertThat(before.getDifficulty()).as("precondition: the world's difficulty is stored as unset").isNull();
        clearInvocations(UltiWorldsTestHelper.getMockLogger());

        commandOver(serviceA).setWorldOption(operator(), WORLD, "description", "Described on server A");

        WorldSettings row = storedRow();
        assertThat(row.getDifficulty())
                .as("server B's difficulty, written into the unset column in the window, is not put back to unset")
                .isEqualTo("HARD");
        assertThat(row.getDescription()).as("server A's change is applied").isEqualTo("Described on server A");
        assertThat(operatorA.rowWrites()).as("A's first write missed and the second, on the re-read row, applied")
                .isEqualTo(2);
        verify(UltiWorldsTestHelper.getMockLogger(), never()).error(anyString());
    }

    @Test
    @DisplayName("probeNullPostCmdWindow: server B adds the first post-teleport command between server A's read and write; A's postcmd add keeps it and appends")
    void probeNullPostCmdWindow() throws Exception {
        String[] rowId = new String[1];
        InterleavingDataOperator<WorldSettings> operatorA = withRowId("post_teleport_commands", "say fromB", rowId);
        WorldService serviceA = serviceOver(operatorA);
        serviceA.getOrCreateSettings(WORLD);
        WorldSettings before = storedRow();
        rowId[0] = before.getId();
        assertThat(before.getPostTeleportCommands()).as("precondition: no post-teleport commands stored").isNull();
        clearInvocations(UltiWorldsTestHelper.getMockLogger());

        commandOver(serviceA).addPostCmd(operator(), WORLD, new String[] {"say", "fromA"});

        WorldSettings row = storedRow();
        assertThat(row.getPostTeleportCommands())
                .as("server B's command, written into the unset column in the window, is kept, then server A's")
                .isEqualTo("say fromB\nsay fromA");
        assertThat(operatorA.rowWrites()).as("A's first write missed and the second, on the re-read row, applied")
                .isEqualTo(2);
        verify(UltiWorldsTestHelper.getMockLogger(), never()).error(anyString());
    }

    @Test
    @DisplayName("Control (SQLite): with no other writer one attempt is made and the unset columns stay unset")
    void noOtherWriterOneAttemptOnSqlite() throws Exception {
        InterleavingDataOperator<WorldSettings> operatorA =
                new InterleavingDataOperator<>(database.openAs(WorldSettings.class), () -> { }, 0);
        WorldService serviceA = serviceOver(operatorA);
        serviceA.getOrCreateSettings(WORLD);
        clearInvocations(UltiWorldsTestHelper.getMockLogger());

        commandOver(serviceA).setWorldOption(operator(), WORLD, "description", "Described on server A");

        WorldSettings row = storedRow();
        assertThat(row.getDescription()).as("server A's change is applied").isEqualTo("Described on server A");
        assertThat(row.getDifficulty()).as("the unset difficulty stays unset").isNull();
        assertThat(row.getPostTeleportCommands()).as("the unset post-teleport commands stay unset").isNull();
        assertThat(operatorA.rowWrites()).as("one attempt: an unset column compares equal to itself").isEqualTo(1);
        verify(UltiWorldsTestHelper.getMockLogger(), never()).error(anyString());
    }

    @Test
    @DisplayName("Control (JSON backend): a never-set world changes on the first attempt, and an unset column is then filled on the first attempt")
    void neverSetWorldChangesOnFirstAttemptOnJson() throws Exception {
        Path jsonDir = Files.createDirectories(dir.resolve("json"));
        DataOperator<WorldSettings> table = new SimpleJsonDataOperator<>(jsonDir.toString(), WorldSettings.class);
        InterleavingDataOperator<WorldSettings> operatorA = new InterleavingDataOperator<>(table, () -> { }, 0);
        WorldService serviceA = serviceOver(operatorA);
        serviceA.getOrCreateSettings(WORLD);
        assertThat(onlyRow(table).getDifficulty()).as("precondition: the difficulty is stored as unset").isNull();
        clearInvocations(UltiWorldsTestHelper.getMockLogger());

        WorldCommand commandA = commandOver(serviceA);
        commandA.setWorldOption(operator(), WORLD, "description", "A JSON world");
        assertThat(operatorA.rowWrites()).as("the first change: one attempt").isEqualTo(1);
        commandA.addPostCmd(operator(), WORLD, new String[] {"say", "one"});
        assertThat(operatorA.rowWrites()).as("the second change, into a column read as unset: one attempt").isEqualTo(2);

        WorldSettings row = onlyRow(table);
        assertThat(row.getDescription()).isEqualTo("A JSON world");
        assertThat(row.getPostTeleportCommands()).isEqualTo("say one");
        assertThat(row.getDifficulty()).as("the unset difficulty stays unset").isNull();
        verify(UltiWorldsTestHelper.getMockLogger(), never()).error(anyString());
    }
}
