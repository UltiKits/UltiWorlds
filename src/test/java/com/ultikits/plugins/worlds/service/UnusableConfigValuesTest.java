package com.ultikits.plugins.worlds.service;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;
import com.ultikits.plugins.worlds.commands.WorldCommand;
import com.ultikits.plugins.worlds.config.WorldConfig;
import com.ultikits.plugins.worlds.entity.WorldSettings;
import com.ultikits.plugins.worlds.i18n.CatalogueText;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.Query;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A configuration value the module cannot use is named, with what is used instead (maintainer
 * decision 2026-09-27: refuse and name). Before, a {@code default_world} naming no loaded world
 * silently sent players to the server's first world, a {@code load_worlds_on_start} entry that
 * could not be loaded was silently skipped, and {@code /world set <world> icon} stored any text,
 * shown as the default icon.
 */
@DisplayName("Configuration values the module cannot use are named")
class UnusableConfigValuesTest {

    @TempDir
    File container;

    private WorldService service;
    private WorldConfig config;
    private UltiToolsPlugin plugin;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        UltiWorldsTestHelper.setUp();
        plugin = UltiWorldsTestHelper.getMockPlugin();
        when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        config = UltiWorldsTestHelper.createDefaultConfig();
        DataOperator<WorldSettings> data = mock(DataOperator.class);
        Query<WorldSettings> query = mock(Query.class);
        when(data.query()).thenReturn(query);
        when(query.where(anyString())).thenReturn(query);
        when(query.eq(any())).thenReturn(query);
        when(plugin.getDataOperator(WorldSettings.class)).thenReturn(data);
        service = new WorldService();
        UltiWorldsTestHelper.setField(service, "config", config);
        UltiWorldsTestHelper.setField(service, "plugin", plugin);
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiWorldsTestHelper.tearDown();
    }

    private List<String> warnings() {
        ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
        verify(UltiWorldsTestHelper.getMockLogger(), atLeast(0)).warn(lines.capture());
        return lines.getAllValues();
    }

    private World world(String name) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        return world;
    }

    @Test
    @DisplayName("a default_world that is no loaded world is named at start, with the world used instead")
    void unknownDefaultWorld() {
        when(config.getDefaultWorld()).thenReturn("Wrold");
        World primary = world("world");
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("Wrold")).thenReturn(null);
            bukkit.when(Bukkit::getWorlds).thenReturn(Collections.singletonList(primary));
            service.init();
        }
        assertThat(warnings()).hasSize(1);
        assertThat(warnings().get(0)).contains("default_world").contains("'Wrold'").contains("'world'");
    }

    @Test
    @DisplayName("control: a default_world that is loaded is quiet")
    void knownDefaultWorld() {
        World primary = world("world");
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(primary);
            bukkit.when(Bukkit::getWorlds).thenReturn(Collections.singletonList(primary));
            service.init();
        }
        assertThat(warnings()).isEmpty();
    }

    @Test
    @DisplayName("a load_worlds_on_start entry that cannot be loaded is named at start")
    void unloadableStartWorld() {
        when(config.getLoadWorldsOnStart()).thenReturn(Arrays.asList("nowhere"));
        World primary = world("world");
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(anyString())).thenReturn(null);
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(primary);
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
            bukkit.when(Bukkit::getWorlds).thenReturn(Collections.singletonList(primary));
            service.init();
        }
        assertThat(warnings()).hasSize(1);
        assertThat(warnings().get(0)).contains("load_worlds_on_start").contains("'nowhere'");
    }

    @Test
    @DisplayName("/world set <world> icon refuses a name that is no item, naming it, and stores nothing")
    void unknownIcon() throws Exception {
        WorldCommand command = new WorldCommand();
        WorldService worldService = mock(WorldService.class);
        UltiWorldsTestHelper.setField(command, "worldService", worldService);
        UltiWorldsTestHelper.setField(command, "plugin", plugin);
        when(worldService.getConfig()).thenReturn(config);
        WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
        settings.setIcon("GRASS_BLOCK");
        when(worldService.getOrCreateSettings("world")).thenReturn(settings);
        Player player = UltiWorldsTestHelper.createMockPlayer("Admin", UUID.randomUUID());
        World world = world("world");
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);

            command.setWorldOption(player, "world", "icon", "grass_blok");
            assertThat(settings.getIcon()).isEqualTo("GRASS_BLOCK");
            verify(worldService, never()).updateSettings(settings);
            verify(player).sendMessage(CatalogueText.text("en", "world.set.invalid_icon").replace("{VALUE}", "grass_blok"));

            command.setWorldOption(player, "world", "icon", "diamond_block");
            assertThat(settings.getIcon()).as("control: a real item is stored").isEqualTo("DIAMOND_BLOCK");
        }
    }
}
