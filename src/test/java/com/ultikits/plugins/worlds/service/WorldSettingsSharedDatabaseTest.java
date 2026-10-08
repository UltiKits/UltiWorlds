package com.ultikits.plugins.worlds.service;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;
import com.ultikits.plugins.worlds.commands.WorldCommand;
import com.ultikits.plugins.worlds.entity.WorldSettings;
import com.ultikits.plugins.worlds.testsupport.SharedSqliteDatabase;
import com.ultikits.ultitools.entities.WhereCondition;
import com.ultikits.ultitools.interfaces.DataOperator;

import org.bukkit.Location;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

/**
 * World settings on servers that share one database
 * (<a href="https://github.com/UltiKits/UltiWorlds/issues/55">UltiWorlds#55</a>) -- the tracer of the
 * shared-database stale-cache class in this module (Phase 17 plan 17-84).
 *
 * <h2>The defect</h2>
 * A server kept each world's settings row in memory for its whole lifetime and wrote that whole copy back
 * on every settings change. Settings rows are keyed by world name, so servers sharing a database share
 * one row per world: a change another server made in between was reverted.
 *
 * <h2>The decision this implements (maintainer, 2026-10-06 00:04)</h2>
 * Every change re-reads the row, applies only what the command changes, and writes it with
 * {@code DataOperator#updateIf} on the values it read, re-reading and re-applying on a miss -- the
 * UltiEconomy pattern UltiTrade#54 follows. Reads stay cached.
 *
 * <h2>What makes a vacuous pass impossible here</h2>
 * Each "server" is a real {@link WorldCommand} over a real {@link WorldService} with its own operator on
 * one SQLite file, so the assertions read the table itself. Before this server acts, the test checks the
 * other server's change really reached the row.
 */
@DisplayName("World settings changed on another server sharing the database are kept (UltiWorlds#55)")
class WorldSettingsSharedDatabaseTest {

    private static final String WORLD = "shared_world";

    @TempDir
    Path dir;

    private SharedSqliteDatabase database;
    private DataOperator<WorldSettings> table;
    private World world;

    @BeforeEach
    void setUp() throws Exception {
        UltiWorldsTestHelper.setUp();
        world = MockBukkit.getMock().addSimpleWorld(WORLD);
        database = SharedSqliteDatabase.in(dir);
        table = database.openAs(WorldSettings.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiWorldsTestHelper.tearDown();
    }

    /** One server: its own service, with its own operator on the shared file, and its own command. */
    private WorldCommand server() throws Exception {
        WorldService service = new WorldService();
        UltiWorldsTestHelper.setField(service, "plugin", UltiWorldsTestHelper.getMockPlugin());
        UltiWorldsTestHelper.setField(service, "config", UltiWorldsTestHelper.createDefaultConfig());
        UltiWorldsTestHelper.setField(service, "dataOperator", database.openAs(WorldSettings.class));
        WorldCommand command = new WorldCommand();
        UltiWorldsTestHelper.setField(command, "plugin", UltiWorldsTestHelper.getMockPlugin());
        UltiWorldsTestHelper.setField(command, "worldService", service);
        return command;
    }

    private static WorldService serviceOf(WorldCommand command) throws Exception {
        java.lang.reflect.Field field = WorldCommand.class.getDeclaredField("worldService");
        field.setAccessible(true); // NOPMD - test reflection
        return (WorldService) field.get(command);
    }

    /** An operator standing in the shared world at {@code x, y, z}. */
    private Player operatorAt(double x, double y, double z) {
        Player player = UltiWorldsTestHelper.createMockPlayer("Admin", UUID.randomUUID());
        Location location = new Location(world, x, y, z, 90f, 0f);
        lenient().when(player.getWorld()).thenReturn(world);
        lenient().when(player.getLocation()).thenReturn(location);
        return player;
    }

    private WorldSettings storedRow() {
        List<WorldSettings> rows = table.getAll(
                WhereCondition.builder().column("world_name").value(WORLD).build());
        assertThat(rows).as("exactly one stored settings row for the world").hasSize(1);
        return rows.get(0);
    }

    @Test
    @DisplayName("PvP turned off on server B survives server A's later /world setspawn; A's spawn is kept too")
    void setSpawnKeepsAnotherServersPvpChange() throws Exception {
        WorldCommand serverA = server();
        WorldCommand serverB = server();
        assertThat(serviceOf(serverA).getOrCreateSettings(WORLD).isPvpEnabled())
                .as("server A has read the world's settings: PvP on").isTrue();
        assertThat(serviceOf(serverB).getOrCreateSettings(WORLD).isPvpEnabled())
                .as("server B has read the same row").isTrue();

        serverB.setWorldOption(operatorAt(0, 64, 0), WORLD, "pvp", "false");
        assertThat(storedRow().isPvpEnabled()).as("precondition: server B's change reached the row").isFalse();

        serverA.setWorldSpawn(operatorAt(120.5, 70, -33.25));

        WorldSettings row = storedRow();
        assertThat(row.isPvpEnabled()).as("server B's change is not reverted").isFalse();
        assertThat(row.getSpawnX()).as("server A's own change is written: spawn x").isEqualTo(120.5);
        assertThat(row.getSpawnY()).as("spawn y").isEqualTo(70.0);
        assertThat(row.getSpawnZ()).as("spawn z").isEqualTo(-33.25);
    }
}
