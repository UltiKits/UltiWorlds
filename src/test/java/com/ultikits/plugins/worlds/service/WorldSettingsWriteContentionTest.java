package com.ultikits.plugins.worlds.service;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;
import com.ultikits.plugins.worlds.commands.WorldCommand;
import com.ultikits.plugins.worlds.entity.WorldSettings;
import com.ultikits.plugins.worlds.testsupport.InterleavingDataOperator;
import com.ultikits.plugins.worlds.testsupport.SharedSqliteDatabase;
import com.ultikits.ultitools.entities.WhereCondition;
import com.ultikits.ultitools.interfaces.DataOperator;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * A world-settings change when another server writes the same row while this server is changing it
 * (UltiKits/UltiWorlds#55; maintainer decision 2026-10-06 00:04: re-read and {@code updateIf} on the
 * values read, re-read and re-apply on a miss, a bounded number of attempts), and when the row is gone
 * (today's UltiWorlds#51 behaviour, pinned).
 * <p>
 * Server A's operator is wrapped so that "server B" -- a second operator on the same SQLite file -- writes
 * the row immediately before A's write, which is exactly the window between A's read and A's write.
 */
@DisplayName("A world-settings change racing another server's write, and a row that is gone (UltiWorlds#55)")
class WorldSettingsWriteContentionTest {

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

    private WorldSettings storedRow() {
        List<WorldSettings> rows = serverB.getAll(
                WhereCondition.builder().column("world_name").value(WORLD).build());
        assertThat(rows).as("exactly one stored settings row for the world").hasSize(1);
        return rows.get(0);
    }

    @Test
    @DisplayName("Server B's write between server A's read and A's write is kept, and A's change is applied on top")
    void writeBetweenReadAndWriteIsKept() throws Exception {
        String[] rowId = new String[1];
        InterleavingDataOperator<WorldSettings> operatorA = new InterleavingDataOperator<>(
                database.openAs(WorldSettings.class),
                () -> serverB.update("description", "Written by server B in between", rowId[0]),
                1);
        WorldService serviceA = serviceOver(operatorA);
        serviceA.getOrCreateSettings(WORLD);
        rowId[0] = storedRow().getId();

        commandOver(serviceA).setWorldOption(operator(), WORLD, "pvp", "false");

        WorldSettings row = storedRow();
        assertThat(row.getDescription()).as("server B's write is kept").isEqualTo("Written by server B in between");
        assertThat(row.isPvpEnabled()).as("server A's change is applied").isFalse();
        assertThat(operatorA.rowWrites()).as("A's first write missed and the second, on the re-read row, applied")
                .isEqualTo(2);
        verify(UltiWorldsTestHelper.getMockLogger(), never()).error(anyString());
    }

    @Test
    @DisplayName("A row that changes before every attempt: A gives up after a bounded number of attempts, logs the save-failed line and writes nothing")
    void everyAttemptLostWritesNothing() throws Exception {
        String[] rowId = new String[1];
        AtomicInteger writesByB = new AtomicInteger();
        InterleavingDataOperator<WorldSettings> operatorA = new InterleavingDataOperator<>(
                database.openAs(WorldSettings.class),
                () -> serverB.update("description", "Server B write " + writesByB.incrementAndGet(), rowId[0]),
                Integer.MAX_VALUE);
        WorldService serviceA = serviceOver(operatorA);
        serviceA.getOrCreateSettings(WORLD);
        rowId[0] = storedRow().getId();
        clearInvocations(UltiWorldsTestHelper.getMockLogger());

        commandOver(serviceA).setWorldOption(operator(), WORLD, "pvp", "false");

        WorldSettings row = storedRow();
        assertThat(row.isPvpEnabled()).as("nothing of server A's is written").isTrue();
        assertThat(row.getDescription()).as("server B's last write stands")
                .isEqualTo("Server B write " + writesByB.get());
        assertThat(operatorA.rowWrites()).as("bounded attempts").isEqualTo(3);
        verify(UltiWorldsTestHelper.getMockLogger(), times(1)).error("log.settings_update_failed");
    }

    @Test
    @DisplayName("A row deleted by another writer: the change logs the save-failed line and is kept in memory only, as before (UltiWorlds#51, pinned)")
    void deletedRowKeepsTodaysBehaviour() throws Exception {
        WorldService serviceA = serviceOver(database.openAs(WorldSettings.class));
        serviceA.getOrCreateSettings(WORLD);
        serverB.delById(storedRow().getId());
        clearInvocations(UltiWorldsTestHelper.getMockLogger());

        commandOver(serviceA).blockWorld(operator(), WORLD);

        verify(UltiWorldsTestHelper.getMockLogger(), times(1)).error("log.settings_update_failed");
        assertThat(serverB.getAll()).as("no row is re-created").isEmpty();
        assertThat(serviceA.getOrCreateSettings(WORLD).isBlocked()).as("the change is kept in memory").isTrue();
    }
}
