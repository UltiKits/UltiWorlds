package com.ultikits.plugins.worlds.gui;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;
import com.ultikits.plugins.worlds.config.WorldConfig;
import com.ultikits.plugins.worlds.entity.WorldSettings;
import com.ultikits.plugins.worlds.service.WorldService;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for WorldListGUI.
 * Each test method opens its own MockedStatic for Bukkit to provide ItemFactory.
 *
 * @author wisdomme
 * @version 2.0.0
 */
@DisplayName("WorldListGUI Tests")
class WorldListGUITest {

    private WorldService mockWorldService;
    private WorldConfig mockConfig;

    @BeforeEach
    void setUp() throws Exception {
        UltiWorldsTestHelper.setUp();
        mockWorldService = mock(WorldService.class);
        mockConfig = UltiWorldsTestHelper.createDefaultConfig();
        when(mockWorldService.getConfig()).thenReturn(mockConfig);
        when(mockConfig.getGuiTitle()).thenReturn("&6World List");
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiWorldsTestHelper.tearDown();
    }

    /**
     * Set up Bukkit static mocks required for ItemStack creation.
     */
    private void setupBukkitMocks(MockedStatic<Bukkit> bukkit) {
        Inventory inventory = mock(Inventory.class);
        bukkit.when(() -> Bukkit.createInventory(any(), eq(54), anyString())).thenReturn(inventory);

        ItemFactory itemFactory = mock(ItemFactory.class);
        ItemMeta itemMeta = mock(ItemMeta.class);
        lenient().when(itemFactory.getItemMeta(any(Material.class))).thenReturn(itemMeta);
        lenient().when(itemFactory.isApplicable(any(ItemMeta.class), any(Material.class))).thenReturn(true);
        bukkit.when(Bukkit::getItemFactory).thenReturn(itemFactory);
    }

    private WorldListGUI createGUI(MockedStatic<Bukkit> bukkit, List<World> worlds) {
        when(mockWorldService.getVisibleWorlds()).thenReturn(worlds);
        setupBukkitMocks(bukkit);

        Player player = UltiWorldsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());

        for (World world : worlds) {
            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings(world.getName());
            when(mockWorldService.getOrCreateSettings(world.getName())).thenReturn(settings);
        }

        return new WorldListGUI(mockWorldService, player);
    }

    private World createMockWorld(String name, World.Environment env) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        when(world.getEnvironment()).thenReturn(env);
        when(world.getPlayers()).thenReturn(Collections.emptyList());
        when(world.getTime()).thenReturn(6000L);
        when(world.isThundering()).thenReturn(false);
        when(world.hasStorm()).thenReturn(false);
        return world;
    }

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("constructor should create GUI with correct viewer")
        void constructorCreatesGui() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
                World world = createMockWorld("world", World.Environment.NORMAL);
                WorldListGUI gui = createGUI(bukkit, Collections.singletonList(world));

                assertThat(gui.getViewer()).isNotNull();
                assertThat(gui.getInventory()).isNotNull();
            }
        }

        @Test
        @DisplayName("constructor should handle empty world list")
        void constructorEmptyWorlds() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
                WorldListGUI gui = createGUI(bukkit, Collections.emptyList());

                assertThat(gui.getViewer()).isNotNull();
                assertThat(gui.getInventory()).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("getWorldAtSlot")
    class GetWorldAtSlot {

        @Test
        @DisplayName("getWorldAtSlot should return world at valid slot")
        void getWorldAtValidSlot() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
                World world1 = createMockWorld("world1", World.Environment.NORMAL);
                World world2 = createMockWorld("world2", World.Environment.NETHER);
                WorldListGUI gui = createGUI(bukkit, Arrays.asList(world1, world2));

                assertThat(gui.getWorldAtSlot(0)).isEqualTo(world1);
                assertThat(gui.getWorldAtSlot(1)).isEqualTo(world2);
            }
        }

        @Test
        @DisplayName("getWorldAtSlot should return null for negative slot")
        void getWorldAtNegativeSlot() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
                World world = createMockWorld("world", World.Environment.NORMAL);
                WorldListGUI gui = createGUI(bukkit, Collections.singletonList(world));

                assertThat(gui.getWorldAtSlot(-1)).isNull();
            }
        }

        @Test
        @DisplayName("getWorldAtSlot should return null for slot beyond items")
        void getWorldAtSlotBeyondItems() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
                World world = createMockWorld("world", World.Environment.NORMAL);
                WorldListGUI gui = createGUI(bukkit, Collections.singletonList(world));

                assertThat(gui.getWorldAtSlot(5)).isNull();
            }
        }

        @Test
        @DisplayName("getWorldAtSlot should return null for slot at ITEMS_PER_PAGE")
        void getWorldAtSlotBeyondPage() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
                World world = createMockWorld("world", World.Environment.NORMAL);
                WorldListGUI gui = createGUI(bukkit, Collections.singletonList(world));

                assertThat(gui.getWorldAtSlot(45)).isNull();
            }
        }
    }

    @Nested
    @DisplayName("Pagination")
    class Pagination {

        @Test
        @DisplayName("nextPage should not increment beyond total pages")
        void nextPageAtEnd() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
                World world = createMockWorld("world", World.Environment.NORMAL);
                WorldListGUI gui = createGUI(bukkit, Collections.singletonList(world));

                gui.nextPage();

                assertThat(gui.getWorldAtSlot(0)).isEqualTo(world);
            }
        }

        @Test
        @DisplayName("previousPage should not decrement below 0")
        void previousPageAtStart() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
                World world = createMockWorld("world", World.Environment.NORMAL);
                WorldListGUI gui = createGUI(bukkit, Collections.singletonList(world));

                gui.previousPage();

                assertThat(gui.getWorldAtSlot(0)).isEqualTo(world);
            }
        }

        @Test
        @DisplayName("nextPage should advance to second page with many worlds")
        void nextPageWithManyWorlds() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
                List<World> worlds = new ArrayList<>();
                for (int i = 0; i < 50; i++) {
                    worlds.add(createMockWorld("world_" + i, World.Environment.NORMAL));
                }
                WorldListGUI gui = createGUI(bukkit, worlds);

                gui.nextPage();

                assertThat(gui.getWorldAtSlot(0)).isEqualTo(worlds.get(45));
            }
        }
    }

    @Nested
    @DisplayName("Helper Methods via Reflection")
    class HelperMethods {

        @Test
        @DisplayName("getDefaultIcon should return correct icons for environments")
        void getDefaultIcon() throws Exception {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
                World world = createMockWorld("world", World.Environment.NORMAL);
                WorldListGUI gui = createGUI(bukkit, Collections.singletonList(world));

                java.lang.reflect.Method method = WorldListGUI.class.getDeclaredMethod("getDefaultIcon", World.Environment.class);
                method.setAccessible(true); // NOPMD - test reflection

                assertThat(method.invoke(gui, World.Environment.NETHER)).isEqualTo(Material.NETHERRACK);
                assertThat(method.invoke(gui, World.Environment.THE_END)).isEqualTo(Material.END_STONE);
                assertThat(method.invoke(gui, World.Environment.NORMAL)).isEqualTo(Material.GRASS_BLOCK);
            }
        }

        @Test
        @DisplayName("getEnvironmentName should return Chinese names")
        void getEnvironmentName() throws Exception {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
                World world = createMockWorld("world", World.Environment.NORMAL);
                WorldListGUI gui = createGUI(bukkit, Collections.singletonList(world));

                java.lang.reflect.Method method = WorldListGUI.class.getDeclaredMethod("getEnvironmentName", World.Environment.class);
                method.setAccessible(true); // NOPMD - test reflection

                assertThat(method.invoke(gui, World.Environment.NORMAL)).isEqualTo("\u4e3b\u4e16\u754c");
                assertThat(method.invoke(gui, World.Environment.NETHER)).isEqualTo("\u4e0b\u754c");
                assertThat(method.invoke(gui, World.Environment.THE_END)).isEqualTo("\u672b\u5730");
            }
        }

        @Test
        @DisplayName("getTimeOfDay should return correct time period")
        void getTimeOfDay() throws Exception {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
                World world = createMockWorld("world", World.Environment.NORMAL);
                WorldListGUI gui = createGUI(bukkit, Collections.singletonList(world));

                java.lang.reflect.Method method = WorldListGUI.class.getDeclaredMethod("getTimeOfDay", World.class);
                method.setAccessible(true); // NOPMD - test reflection

                World morningWorld = mock(World.class);
                when(morningWorld.getTime()).thenReturn(3000L);
                assertThat(method.invoke(gui, morningWorld)).isEqualTo("\u65e9\u6668");

                World noonWorld = mock(World.class);
                when(noonWorld.getTime()).thenReturn(9000L);
                assertThat(method.invoke(gui, noonWorld)).isEqualTo("\u4e2d\u5348");

                World eveningWorld = mock(World.class);
                when(eveningWorld.getTime()).thenReturn(15000L);
                assertThat(method.invoke(gui, eveningWorld)).isEqualTo("\u508d\u665a");

                World nightWorld = mock(World.class);
                when(nightWorld.getTime()).thenReturn(20000L);
                assertThat(method.invoke(gui, nightWorld)).isEqualTo("\u591c\u665a");
            }
        }

        @Test
        @DisplayName("getWeather should return correct weather")
        void getWeather() throws Exception {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
                World world = createMockWorld("world", World.Environment.NORMAL);
                WorldListGUI gui = createGUI(bukkit, Collections.singletonList(world));

                java.lang.reflect.Method method = WorldListGUI.class.getDeclaredMethod("getWeather", World.class);
                method.setAccessible(true); // NOPMD - test reflection

                World thunderWorld = mock(World.class);
                when(thunderWorld.isThundering()).thenReturn(true);
                assertThat(method.invoke(gui, thunderWorld)).isEqualTo("\u96f7\u66b4");

                World rainWorld = mock(World.class);
                when(rainWorld.isThundering()).thenReturn(false);
                when(rainWorld.hasStorm()).thenReturn(true);
                assertThat(method.invoke(gui, rainWorld)).isEqualTo("\u4e0b\u96e8");

                World clearWorld = mock(World.class);
                when(clearWorld.isThundering()).thenReturn(false);
                when(clearWorld.hasStorm()).thenReturn(false);
                assertThat(method.invoke(gui, clearWorld)).isEqualTo("\u6674\u6717");
            }
        }
    }

    @Nested
    @DisplayName("World Item Creation")
    class WorldItemCreation {

        @Test
        @DisplayName("createWorldItem should handle world with description")
        void createWorldItemWithDescription() throws Exception {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
                World world = createMockWorld("world", World.Environment.NORMAL);
                WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
                settings.setDescription("A test world");
                when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

                WorldListGUI gui = createGUI(bukkit, Collections.singletonList(world));

                java.lang.reflect.Method method = WorldListGUI.class.getDeclaredMethod("createWorldItem", World.class);
                method.setAccessible(true); // NOPMD - test reflection

                Object result = method.invoke(gui, world);
                assertThat(result).isNotNull();
            }
        }

        @Test
        @DisplayName("createWorldItem should handle locked world")
        void createWorldItemLocked() throws Exception {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
                World world = createMockWorld("world", World.Environment.NORMAL);
                WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
                settings.setLocked(true);
                when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

                WorldListGUI gui = createGUI(bukkit, Collections.singletonList(world));

                java.lang.reflect.Method method = WorldListGUI.class.getDeclaredMethod("createWorldItem", World.class);
                method.setAccessible(true); // NOPMD - test reflection

                Object result = method.invoke(gui, world);
                assertThat(result).isNotNull();
            }
        }

        @Test
        @DisplayName("createWorldItem should handle NETHER environment icon")
        void createWorldItemNetherIcon() throws Exception {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
                World world = createMockWorld("nether", World.Environment.NETHER);
                WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("nether");
                settings.setIcon("INVALID_ICON");
                when(mockWorldService.getOrCreateSettings("nether")).thenReturn(settings);

                WorldListGUI gui = createGUI(bukkit, Collections.singletonList(world));

                java.lang.reflect.Method method = WorldListGUI.class.getDeclaredMethod("createWorldItem", World.class);
                method.setAccessible(true); // NOPMD - test reflection

                Object result = method.invoke(gui, world);
                assertThat(result).isNotNull();
            }
        }

        @Test
        @DisplayName("createWorldItem should handle null display name")
        void createWorldItemNullDisplayName() throws Exception {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
                World world = createMockWorld("world", World.Environment.NORMAL);
                WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
                settings.setDisplayName(null);
                when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

                WorldListGUI gui = createGUI(bukkit, Collections.singletonList(world));

                java.lang.reflect.Method method = WorldListGUI.class.getDeclaredMethod("createWorldItem", World.class);
                method.setAccessible(true); // NOPMD - test reflection

                Object result = method.invoke(gui, world);
                assertThat(result).isNotNull();
            }
        }
    }
}
