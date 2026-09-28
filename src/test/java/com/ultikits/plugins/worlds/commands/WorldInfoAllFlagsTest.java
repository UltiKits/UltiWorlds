package com.ultikits.plugins.worlds.commands;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;
import com.ultikits.plugins.worlds.entity.WorldSettings;
import com.ultikits.plugins.worlds.i18n.CatalogueText;
import com.ultikits.plugins.worlds.service.WorldService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every boolean a world's settings store can be read back with {@code /world info [world]} and
 * changed with {@code /world set} (UltiKits/UltiWorlds#17). The flags are enumerated from
 * {@link WorldSettings}' own fields, so a boolean added later without a display or set path fails
 * here.
 */
@DisplayName("/world info shows, and /world set changes, every world flag (UltiKits/UltiWorlds#17)")
class WorldInfoAllFlagsTest {

    private WorldCommand command;
    private WorldService worldService;
    private WorldSettings settings;
    private Player player;
    private World world;

    @BeforeEach
    void setUp() throws Exception {
        UltiWorldsTestHelper.setUp();
        UltiToolsPlugin plugin = UltiWorldsTestHelper.getMockPlugin();
        // The real English text, so a flag's value is really inserted into its line.
        when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        command = new WorldCommand();
        worldService = mock(WorldService.class);
        UltiWorldsTestHelper.setField(command, "worldService", worldService);
        UltiWorldsTestHelper.setField(command, "plugin", plugin);
        com.ultikits.plugins.worlds.config.WorldConfig config = UltiWorldsTestHelper.createDefaultConfig();
        when(worldService.getConfig()).thenReturn(config);

        player = UltiWorldsTestHelper.createMockPlayer("Viewer", UUID.randomUUID());
        world = player.getWorld();
        when(world.getName()).thenReturn("world");
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getSeed()).thenReturn(1L);
        when(world.getPlayers()).thenReturn(Collections.emptyList());
        settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
        when(worldService.getOrCreateSettings("world")).thenReturn(settings);
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiWorldsTestHelper.tearDown();
    }

    /** Every instance boolean of {@link WorldSettings}. */
    private static List<Field> flags() {
        List<Field> flags = new ArrayList<>();
        for (Field field : WorldSettings.class.getDeclaredFields()) {
            if (field.getType() == boolean.class && !Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true); // NOPMD - enumerating the entity's own flags
                flags.add(field);
            }
        }
        return flags;
    }

    /** The {@code /world set} option name for a flag: its field name, without an "Enabled" suffix. */
    private static String option(Field flag) {
        String name = flag.getName();
        return name.endsWith("Enabled") ? name.substring(0, name.length() - "Enabled".length()) : name;
    }

    private void setAll(boolean value) throws IllegalAccessException {
        for (Field flag : flags()) {
            flag.setBoolean(settings, value);
        }
    }

    private List<String> infoLines() {
        clearInvocations(player);
        command.worldInfo(player);
        ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
        verify(player, atLeast(1)).sendMessage(lines.capture());
        return lines.getAllValues();
    }

    @Test
    @DisplayName("control: WorldSettings has the twelve flags this test enumerates")
    void twelveFlags() {
        assertThat(flags()).extracting(Field::getName).containsExactlyInAnyOrder(
                "pvpEnabled", "monstersEnabled", "animalsEnabled", "weatherEnabled", "hidden", "locked",
                "blocked", "autoUnload", "protectBreak", "protectPlace", "protectInteract", "protectExplosion");
    }

    @Test
    @DisplayName("turning any one flag on changes the /world info output")
    void everyFlagIsDisplayed() throws Exception {
        for (Field flag : flags()) {
            setAll(false);
            List<String> off = infoLines();
            flag.setBoolean(settings, true);
            List<String> on = infoLines();
            assertThat(on).as("/world info with only %s on", flag.getName()).isNotEqualTo(off);
        }
    }

    @Test
    @DisplayName("/world set accepts every flag by name, on and off")
    void everyFlagCanBeSet() throws Exception {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            for (Field flag : flags()) {
                command.setWorldOption(player, "world", option(flag), "true");
                assertThat(flag.getBoolean(settings)).as("set %s true", option(flag)).isTrue();
                command.setWorldOption(player, "world", option(flag), "false");
                assertThat(flag.getBoolean(settings)).as("set %s false", option(flag)).isFalse();
            }
        }
    }

    @Test
    @DisplayName("/world info <world> shows the named world, and names a world that is not loaded")
    void infoOfANamedWorld() throws Exception {
        Method named = WorldCommand.class.getMethod("worldInfo", Player.class, String.class);
        World other = mock(World.class);
        when(other.getName()).thenReturn("other");
        when(other.getEnvironment()).thenReturn(World.Environment.NETHER);
        when(other.getPlayers()).thenReturn(Collections.emptyList());
        WorldSettings otherSettings = UltiWorldsTestHelper.createSampleWorldSettings("other");
        when(worldService.getOrCreateSettings("other")).thenReturn(otherSettings);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("other")).thenReturn(other);
            named.invoke(command, player, "other");
            verify(player).sendMessage("§7Name: §fother");
            verify(player).sendMessage("§7Environment: §fNETHER");

            named.invoke(command, player, "missing");
            verify(player).sendMessage(CatalogueText.text("en", "world.not_found").replace("{WORLD}", "missing"));
        }
    }
}
