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
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

/**
 * Every command that changes a world setting keeps a change another server sharing the database made to
 * another field (UltiKits/UltiWorlds#55; maintainer decision 2026-10-06 00:04) -- one test per place the
 * module wrote a world's settings: {@code /world set} (one call site; several options), {@code protect},
 * {@code unprotect}, {@code block}, {@code unblock}, {@code difficulty}, {@code postcmd add},
 * {@code postcmd clear} and {@code setspawn}.
 * <p>
 * Shape of each test: server A reads the world's row (and keeps it, as every server does); server B then
 * changes the description and the monsters flag -- fields no command of A's touches here -- and the test
 * asserts B's change reached the row; A runs its command; the stored row must hold B's change and A's.
 * On the base code A wrote its whole cached copy, so B's change was reverted.
 * <p>
 * A separate test covers a column the stored row holds as {@code NULL} when A reads it ({@code difficulty}
 * of a new world): B sets it; A's later, unrelated command must not put the {@code NULL} back.
 */
@DisplayName("Every world-settings command keeps another server's change to another field (UltiWorlds#55)")
class WorldSettingsCommandsSharedDatabaseTest {

    private static final String WORLD = "shared_world";
    private static final String FROM_B = "Described on server B";

    @TempDir
    Path dir;

    private SharedSqliteDatabase database;
    private DataOperator<WorldSettings> table;
    private World world;
    private WorldCommand serverA;
    private WorldCommand serverB;

    @BeforeEach
    void setUp() throws Exception {
        UltiWorldsTestHelper.setUp();
        world = MockBukkit.getMock().addSimpleWorld(WORLD);
        database = SharedSqliteDatabase.in(dir);
        table = database.openAs(WorldSettings.class);
        serverA = server();
        serverB = server();
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

    private Player operator() {
        return operatorAt(0, 64, 0);
    }

    private Player operatorAt(double x, double y, double z) {
        Player player = UltiWorldsTestHelper.createMockPlayer("Admin", UUID.randomUUID());
        Location location = new Location(world, x, y, z, 45f, 10f);
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

    /** A command of server A's, run against the shared world. */
    private interface Action {
        void run(WorldCommand server) throws Exception;
    }

    /**
     * Server A holds the row (after {@code setUpOnA}, if any); server B changes the description and the
     * monsters flag; server A runs {@code onA}; the stored row must keep B's change and show A's.
     */
    private void assertKeepsServerBsChange(Action setUpOnA, Action onA, Consumer<WorldSettings> aWrote)
            throws Exception {
        if (setUpOnA != null) {
            setUpOnA.run(serverA);
        }
        serviceOf(serverA).getOrCreateSettings(WORLD);
        serviceOf(serverB).getOrCreateSettings(WORLD);

        serverB.setWorldOption(operator(), WORLD, "description", FROM_B);
        serverB.setWorldOption(operator(), WORLD, "monsters", "false");
        WorldSettings afterB = storedRow();
        assertThat(afterB.getDescription()).as("precondition: server B's description reached the row").isEqualTo(FROM_B);
        assertThat(afterB.isMonstersEnabled()).as("precondition: server B's monsters flag reached the row").isFalse();

        onA.run(serverA);

        WorldSettings row = storedRow();
        assertThat(row.getDescription()).as("server B's description is not reverted").isEqualTo(FROM_B);
        assertThat(row.isMonstersEnabled()).as("server B's monsters flag is not reverted").isFalse();
        aWrote.accept(row);
    }

    @Test
    @DisplayName("/world set <world> pvp false")
    void setPvp() throws Exception {
        assertKeepsServerBsChange(null,
                a -> a.setWorldOption(operator(), WORLD, "pvp", "false"),
                row -> assertThat(row.isPvpEnabled()).as("server A's pvp change").isFalse());
    }

    @Test
    @DisplayName("/world set <world> displayname <text>")
    void setDisplayName() throws Exception {
        assertKeepsServerBsChange(null,
                a -> a.setWorldOption(operator(), WORLD, "displayname", "Shown on A"),
                row -> assertThat(row.getDisplayName()).as("server A's display name").isEqualTo("Shown on A"));
    }

    @Test
    @DisplayName("/world set <world> icon DIAMOND")
    void setIcon() throws Exception {
        assertKeepsServerBsChange(null,
                a -> a.setWorldOption(operator(), WORLD, "icon", "diamond"),
                row -> assertThat(row.getIcon()).as("server A's icon").isEqualTo("DIAMOND"));
    }

    @Test
    @DisplayName("/world set <world> difficulty hard")
    void setDifficultyOption() throws Exception {
        assertKeepsServerBsChange(null,
                a -> a.setWorldOption(operator(), WORLD, "difficulty", "hard"),
                row -> assertThat(row.getDifficulty()).as("server A's difficulty").isEqualTo("HARD"));
    }

    @Test
    @DisplayName("/world protect <world>")
    void protect() throws Exception {
        assertKeepsServerBsChange(null,
                a -> a.protectWorld(operator(), WORLD),
                row -> {
                    assertThat(row.isProtectBreak()).as("protect_break").isTrue();
                    assertThat(row.isProtectPlace()).as("protect_place").isTrue();
                    assertThat(row.isProtectInteract()).as("protect_interact").isTrue();
                    assertThat(row.isProtectExplosion()).as("protect_explosion").isTrue();
                });
    }

    @Test
    @DisplayName("/world unprotect <world>")
    void unprotect() throws Exception {
        assertKeepsServerBsChange(
                a -> a.protectWorld(operator(), WORLD),
                a -> a.unprotectWorld(operator(), WORLD),
                row -> assertThat(row.hasProtection()).as("server A removed every protection").isFalse());
    }

    @Test
    @DisplayName("/world block <world>")
    void block() throws Exception {
        assertKeepsServerBsChange(null,
                a -> a.blockWorld(operator(), WORLD),
                row -> assertThat(row.isBlocked()).as("server A's block").isTrue());
    }

    @Test
    @DisplayName("/world unblock <world>")
    void unblock() throws Exception {
        assertKeepsServerBsChange(
                a -> a.blockWorld(operator(), WORLD),
                a -> a.unblockWorld(operator(), WORLD),
                row -> assertThat(row.isBlocked()).as("server A's unblock").isFalse());
    }

    @Test
    @DisplayName("/world difficulty <world> <level>")
    void difficultyCommand() throws Exception {
        assertKeepsServerBsChange(null,
                a -> a.setDifficulty(operator(), WORLD, "peaceful"),
                row -> assertThat(row.getDifficulty()).as("server A's difficulty").isEqualTo("PEACEFUL"));
    }

    @Test
    @DisplayName("/world postcmd <world> add <command>")
    void postCommandAdd() throws Exception {
        assertKeepsServerBsChange(null,
                a -> a.addPostCmd(operator(), WORLD, new String[] {"say", "hello"}),
                row -> assertThat(row.getPostTeleportCommands()).as("server A's command").isEqualTo("say hello"));
    }

    @Test
    @DisplayName("/world postcmd <world> clear")
    void postCommandClear() throws Exception {
        assertKeepsServerBsChange(
                a -> a.addPostCmd(operator(), WORLD, new String[] {"say", "hello"}),
                a -> a.clearPostCmd(operator(), WORLD),
                row -> assertThat(row.getPostTeleportCommands()).as("server A cleared the commands").isNull());
    }

    @Test
    @DisplayName("/world setspawn")
    void setSpawn() throws Exception {
        assertKeepsServerBsChange(null,
                a -> a.setWorldSpawn(operatorAt(-7.5, 80, 12.25)),
                row -> {
                    assertThat(row.getSpawnX()).as("spawn x").isEqualTo(-7.5);
                    assertThat(row.getSpawnY()).as("spawn y").isEqualTo(80.0);
                    assertThat(row.getSpawnZ()).as("spawn z").isEqualTo(12.25);
                    assertThat(row.getSpawnYaw()).as("spawn yaw").isEqualTo(45f);
                    assertThat(row.getSpawnPitch()).as("spawn pitch").isEqualTo(10f);
                });
    }

    @Test
    @DisplayName("A column server A read as NULL (a new world's difficulty) and server B then set is not put back to NULL")
    void columnReadAsNullIsNotPutBack() throws Exception {
        assertThat(serviceOf(serverA).getOrCreateSettings(WORLD).getDifficulty())
                .as("server A has read the row: no difficulty stored").isNull();
        serviceOf(serverB).getOrCreateSettings(WORLD);

        serverB.setDifficulty(operator(), WORLD, "hard");
        assertThat(storedRow().getDifficulty()).as("precondition: server B's difficulty reached the row").isEqualTo("HARD");

        serverA.blockWorld(operator(), WORLD);

        WorldSettings row = storedRow();
        assertThat(row.getDifficulty()).as("server B's difficulty is not put back to NULL").isEqualTo("HARD");
        assertThat(row.isBlocked()).as("server A's block is written").isTrue();
    }

    @Test
    @DisplayName("After its own change, server A's running checks read the row as written, with server B's change")
    void ownChangeRefreshesTheReadCache() throws Exception {
        serviceOf(serverA).getOrCreateSettings(WORLD);
        serviceOf(serverB).getOrCreateSettings(WORLD);
        serverB.setWorldOption(operator(), WORLD, "locked", "true");

        serverA.setWorldOption(operator(), WORLD, "hidden", "true");

        WorldSettings held = serviceOf(serverA).getOrCreateSettings(WORLD);
        assertThat(held.isHidden()).as("server A's own change").isTrue();
        assertThat(held.isLocked()).as("server B's change, read back by server A's change").isTrue();
    }
}
