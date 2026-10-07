package com.ultikits.plugins.worlds.commands;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;
import com.ultikits.plugins.worlds.entity.WorldSettings;
import com.ultikits.plugins.worlds.service.WorldService;
import com.ultikits.plugins.worlds.testsupport.SharedSqliteDatabase;
import com.ultikits.ultitools.entities.WhereCondition;
import com.ultikits.ultitools.interfaces.DataOperator;

import org.bukkit.Difficulty;
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
 * A difficulty change on a world whose settings this server has not read yet (a world another plugin
 * loaded after start-up that nothing has touched since) while a difficulty is already stored for it
 * (UltiKits/UltiWorlds#55, local Codex review run 2 on PR #56).
 * <p>
 * The first read of a world's settings applies the stored difficulty to the world. {@code /world set
 * <world> difficulty} set the world's difficulty before the settings change read the row, so on such a
 * world that first read put the old stored difficulty back on the world: the database held the new
 * value, the command reported success, and the world kept the old one. Before the change for #55 the
 * command read the settings first, and the order was right.
 * <p>
 * Server B stores {@code EASY} through its own command; server A, which has read nothing, then runs the
 * command. Real SQLite, the module's own two-server harness.
 */
@DisplayName("A difficulty change on a world this server has not read settings for reaches the world (UltiWorlds#55)")
class WorldSetDifficultyUncachedWorldTest {

    private static final String WORLD = "uncached_world";

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

    private Player operator() {
        Player player = UltiWorldsTestHelper.createMockPlayer("Admin", UUID.randomUUID());
        lenient().when(player.getWorld()).thenReturn(world);
        return player;
    }

    private String storedDifficulty() {
        List<WorldSettings> rows = table.getAll(
                WhereCondition.builder().column("world_name").value(WORLD).build());
        assertThat(rows).as("exactly one stored settings row for the world").hasSize(1);
        return rows.get(0).getDifficulty();
    }

    /** Server B stores EASY; the world is EASY. Returns server A, which has read no settings yet. */
    private WorldCommand serverAWithEasyStored() throws Exception {
        server().setDifficulty(operator(), WORLD, "easy");
        assertThat(storedDifficulty()).as("precondition: EASY is stored").isEqualTo("EASY");
        assertThat(world.getDifficulty()).as("precondition: the world is EASY").isEqualTo(Difficulty.EASY);
        return server();
    }

    @Test
    @DisplayName("/world set <world> difficulty hard: the world becomes HARD, and HARD is stored")
    void setOptionReachesTheWorld() throws Exception {
        WorldCommand serverA = serverAWithEasyStored();

        serverA.setWorldOption(operator(), WORLD, "difficulty", "hard");

        assertThat(world.getDifficulty()).as("the world's own difficulty is the one just set, not the old stored one")
                .isEqualTo(Difficulty.HARD);
        assertThat(storedDifficulty()).as("the stored difficulty").isEqualTo("HARD");
    }

    @Test
    @DisplayName("Control: /world difficulty <world> hard on the same kind of world")
    void difficultyCommandReachesTheWorld() throws Exception {
        WorldCommand serverA = serverAWithEasyStored();

        serverA.setDifficulty(operator(), WORLD, "hard");

        assertThat(world.getDifficulty()).isEqualTo(Difficulty.HARD);
        assertThat(storedDifficulty()).isEqualTo("HARD");
    }
}
