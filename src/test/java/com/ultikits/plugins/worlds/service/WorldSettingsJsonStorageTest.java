package com.ultikits.plugins.worlds.service;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;
import com.ultikits.plugins.worlds.commands.WorldCommand;
import com.ultikits.plugins.worlds.entity.WorldSettings;
import com.ultikits.ultitools.entities.WhereCondition;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.impl.data.json.SimpleJsonDataOperator;

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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Guard for the conditional settings write on the JSON storage backend (UltiKits/UltiWorlds#55): the
 * values a change is conditioned on must compare equal to the stored ones on every backend, or every
 * change would be refused as contended. One server, the framework's real {@link SimpleJsonDataOperator}:
 * every settings command, in sequence, is written and none logs the save-failed line.
 * <p>
 * This is a guard, not a red-when-reverted proof: the whole-object write before the fix passes it too.
 */
@DisplayName("Every world-settings command writes on the JSON backend (UltiWorlds#55 guard)")
class WorldSettingsJsonStorageTest {

    private static final String WORLD = "json_world";

    @TempDir
    Path dir;

    private World world;

    @BeforeEach
    void setUp() throws Exception {
        UltiWorldsTestHelper.setUp();
        world = MockBukkit.getMock().addSimpleWorld(WORLD);
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiWorldsTestHelper.tearDown();
    }

    @Test
    @DisplayName("Each command's change is stored, with every earlier one, and nothing is reported as failed")
    void everyCommandWrites() throws Exception {
        DataOperator<WorldSettings> table = new SimpleJsonDataOperator<>(dir.toString(), WorldSettings.class);
        WorldService service = new WorldService();
        UltiWorldsTestHelper.setField(service, "plugin", UltiWorldsTestHelper.getMockPlugin());
        UltiWorldsTestHelper.setField(service, "config", UltiWorldsTestHelper.createDefaultConfig());
        UltiWorldsTestHelper.setField(service, "dataOperator", table);
        WorldCommand command = new WorldCommand();
        UltiWorldsTestHelper.setField(command, "plugin", UltiWorldsTestHelper.getMockPlugin());
        UltiWorldsTestHelper.setField(command, "worldService", service);

        Player player = UltiWorldsTestHelper.createMockPlayer("Admin", UUID.randomUUID());
        lenient().when(player.getWorld()).thenReturn(world);
        lenient().when(player.getLocation()).thenReturn(new Location(world, 1.25, 70, -3.5, 33.3f, -12.7f));

        command.setWorldOption(player, WORLD, "pvp", "false");
        command.setWorldOption(player, WORLD, "description", "A JSON world");
        command.setDifficulty(player, WORLD, "hard");
        command.protectWorld(player, WORLD);
        command.blockWorld(player, WORLD);
        command.addPostCmd(player, WORLD, new String[] {"say", "one"});
        command.addPostCmd(player, WORLD, new String[] {"say", "two"});
        command.setWorldSpawn(player);
        command.setWorldOption(player, WORLD, "hidden", "true");

        List<WorldSettings> rows = table.getAll(WhereCondition.builder().column("world_name").value(WORLD).build());
        assertThat(rows).hasSize(1);
        WorldSettings row = rows.get(0);
        assertThat(row.isPvpEnabled()).isFalse();
        assertThat(row.getDescription()).isEqualTo("A JSON world");
        assertThat(row.getDifficulty()).isEqualTo("HARD");
        assertThat(row.hasProtection()).isTrue();
        assertThat(row.isBlocked()).isTrue();
        assertThat(row.getPostTeleportCommands()).isEqualTo("say one\nsay two");
        assertThat(row.getSpawnX()).isEqualTo(1.25);
        assertThat(row.getSpawnYaw()).isEqualTo(33.3f);
        assertThat(row.isHidden()).isTrue();
        verify(UltiWorldsTestHelper.getMockLogger(), never()).error(anyString());
    }
}
