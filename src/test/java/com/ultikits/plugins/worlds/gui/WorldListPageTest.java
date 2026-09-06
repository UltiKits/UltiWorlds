package com.ultikits.plugins.worlds.gui;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;
import com.ultikits.plugins.worlds.entity.WorldSettings;
import com.ultikits.plugins.worlds.service.WorldService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import mc.obliviate.inventory.Icon;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

import java.lang.reflect.Method;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for WorldListPage GUI methods.
 * Tests private helper methods via reflection since the GUI framework
 * classes cannot be opened in tests without a live Bukkit server.
 *
 * @author wisdomme
 * @version 2.0.0
 */
@DisplayName("WorldListPage Tests")
class WorldListPageTest {

    private WorldService mockWorldService;
    private UltiToolsPlugin mockPlugin;

    @BeforeEach
    void setUp() throws Exception {
        // UltiWorldsTestHelper.setUp() installs a live MockBukkit server (phase-14 bootstrap),
        // which already answers ItemFactory/registry lookups for real ItemStack construction. A
        // hand-rolled Bukkit.setServer(mock(Server.class)) used to run after this and silently
        // replace that live server with a bare Mockito mock stubbing only getItemFactory() -- a
        // stand-in that could not satisfy Tag/Registry resolution, permanently poisoning
        // registry-backed classes (MaterialTags/BlockStateMetaMock) for the rest of the Surefire
        // fork. Removed; the live server is sufficient on its own.
        UltiWorldsTestHelper.setUp();
        mockPlugin = UltiWorldsTestHelper.getMockPlugin();
        mockWorldService = mock(WorldService.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiWorldsTestHelper.tearDown();
    }

    private WorldListPage createPage(Player player) {
        return new WorldListPage(player, mockWorldService, mockPlugin);
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("constructor should create page with correct parameters")
        void constructorCreatesPage() {
            Player player = UltiWorldsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());

            WorldListPage page = createPage(player);

            assertThat(page).isNotNull();
        }
    }

    @Nested
    @DisplayName("provideItems")
    class ProvideItemsTests {

        @Test
        @DisplayName("provideItems should return icons for visible worlds")
        void provideItemsReturnsIcons() throws Exception {
            Player player = UltiWorldsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());

            World world1 = mock(World.class);
            when(world1.getName()).thenReturn("world1");
            when(world1.getEnvironment()).thenReturn(World.Environment.NORMAL);
            when(world1.getPlayers()).thenReturn(Collections.emptyList());
            when(world1.getTime()).thenReturn(6000L);
            when(world1.isThundering()).thenReturn(false);
            when(world1.hasStorm()).thenReturn(false);

            when(mockWorldService.getVisibleWorlds()).thenReturn(Collections.singletonList(world1));

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world1");
            when(mockWorldService.getOrCreateSettings("world1")).thenReturn(settings);

            WorldListPage page = createPage(player);

            Method method = WorldListPage.class.getDeclaredMethod("provideItems");
            method.setAccessible(true); // NOPMD - test reflection

            @SuppressWarnings("unchecked")
            List<Icon> icons = (List<Icon>) method.invoke(page);

            assertThat(icons).hasSize(1);
        }

        @Test
        @DisplayName("provideItems should return empty list for no worlds")
        void provideItemsEmpty() throws Exception {
            Player player = UltiWorldsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());

            when(mockWorldService.getVisibleWorlds()).thenReturn(Collections.emptyList());

            WorldListPage page = createPage(player);

            Method method = WorldListPage.class.getDeclaredMethod("provideItems");
            method.setAccessible(true); // NOPMD - test reflection

            @SuppressWarnings("unchecked")
            List<Icon> icons = (List<Icon>) method.invoke(page);

            assertThat(icons).isEmpty();
        }

        @Test
        @DisplayName("provideItems should handle multiple worlds with different environments")
        void provideItemsMultipleWorlds() throws Exception {
            Player player = UltiWorldsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());

            World world1 = mock(World.class);
            when(world1.getName()).thenReturn("world");
            when(world1.getEnvironment()).thenReturn(World.Environment.NORMAL);
            when(world1.getPlayers()).thenReturn(Collections.emptyList());
            when(world1.getTime()).thenReturn(3000L);
            when(world1.isThundering()).thenReturn(false);
            when(world1.hasStorm()).thenReturn(false);

            World world2 = mock(World.class);
            when(world2.getName()).thenReturn("nether");
            when(world2.getEnvironment()).thenReturn(World.Environment.NETHER);
            when(world2.getPlayers()).thenReturn(Collections.emptyList());
            when(world2.getTime()).thenReturn(15000L);
            when(world2.isThundering()).thenReturn(true);
            when(world2.hasStorm()).thenReturn(false);

            World world3 = mock(World.class);
            when(world3.getName()).thenReturn("end");
            when(world3.getEnvironment()).thenReturn(World.Environment.THE_END);
            when(world3.getPlayers()).thenReturn(Collections.emptyList());
            when(world3.getTime()).thenReturn(20000L);
            when(world3.isThundering()).thenReturn(false);
            when(world3.hasStorm()).thenReturn(true);

            when(mockWorldService.getVisibleWorlds()).thenReturn(Arrays.asList(world1, world2, world3));

            WorldSettings settings1 = UltiWorldsTestHelper.createSampleWorldSettings("world");
            WorldSettings settings2 = UltiWorldsTestHelper.createSampleWorldSettings("nether");
            settings2.setIcon("NETHERRACK");
            WorldSettings settings3 = UltiWorldsTestHelper.createSampleWorldSettings("end");
            settings3.setIcon("END_STONE");

            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings1);
            when(mockWorldService.getOrCreateSettings("nether")).thenReturn(settings2);
            when(mockWorldService.getOrCreateSettings("end")).thenReturn(settings3);

            WorldListPage page = createPage(player);

            Method method = WorldListPage.class.getDeclaredMethod("provideItems");
            method.setAccessible(true); // NOPMD - test reflection

            @SuppressWarnings("unchecked")
            List<Icon> icons = (List<Icon>) method.invoke(page);

            assertThat(icons).hasSize(3);
        }
    }

    @Nested
    @DisplayName("Helper Methods")
    class HelperMethods {

        @Test
        @DisplayName("getDefaultIcon should return correct icon for each environment")
        void getDefaultIcon() throws Exception {
            Player player = UltiWorldsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());
            WorldListPage page = createPage(player);

            Method method = WorldListPage.class.getDeclaredMethod("getDefaultIcon", World.Environment.class);
            method.setAccessible(true); // NOPMD - test reflection

            assertThat(method.invoke(page, World.Environment.NETHER)).isEqualTo(Material.NETHERRACK);
            assertThat(method.invoke(page, World.Environment.THE_END)).isEqualTo(Material.END_STONE);
            assertThat(method.invoke(page, World.Environment.NORMAL)).isEqualTo(Material.GRASS_BLOCK);
        }

        @Test
        @DisplayName("getEnvironmentName should return i18n keys")
        void getEnvironmentName() throws Exception {
            Player player = UltiWorldsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());
            WorldListPage page = createPage(player);

            Method method = WorldListPage.class.getDeclaredMethod("getEnvironmentName", World.Environment.class);
            method.setAccessible(true); // NOPMD - test reflection

            assertThat(method.invoke(page, World.Environment.NORMAL)).isEqualTo("environment.normal");
            assertThat(method.invoke(page, World.Environment.NETHER)).isEqualTo("environment.nether");
            assertThat(method.invoke(page, World.Environment.THE_END)).isEqualTo("environment.the_end");
        }

        @Test
        @DisplayName("getTimeOfDay should return i18n time period keys")
        void getTimeOfDay() throws Exception {
            Player player = UltiWorldsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());
            WorldListPage page = createPage(player);

            Method method = WorldListPage.class.getDeclaredMethod("getTimeOfDay", World.class);
            method.setAccessible(true); // NOPMD - test reflection

            World morningWorld = mock(World.class);
            when(morningWorld.getTime()).thenReturn(3000L);
            assertThat(method.invoke(page, morningWorld)).isEqualTo("time.morning");

            World noonWorld = mock(World.class);
            when(noonWorld.getTime()).thenReturn(9000L);
            assertThat(method.invoke(page, noonWorld)).isEqualTo("time.noon");

            World eveningWorld = mock(World.class);
            when(eveningWorld.getTime()).thenReturn(15000L);
            assertThat(method.invoke(page, eveningWorld)).isEqualTo("time.evening");

            World nightWorld = mock(World.class);
            when(nightWorld.getTime()).thenReturn(20000L);
            assertThat(method.invoke(page, nightWorld)).isEqualTo("time.night");
        }

        @Test
        @DisplayName("getWeather should return i18n weather keys")
        void getWeather() throws Exception {
            Player player = UltiWorldsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());
            WorldListPage page = createPage(player);

            Method method = WorldListPage.class.getDeclaredMethod("getWeather", World.class);
            method.setAccessible(true); // NOPMD - test reflection

            World thunderWorld = mock(World.class);
            when(thunderWorld.isThundering()).thenReturn(true);
            assertThat(method.invoke(page, thunderWorld)).isEqualTo("weather.thunder");

            World rainWorld = mock(World.class);
            when(rainWorld.isThundering()).thenReturn(false);
            when(rainWorld.hasStorm()).thenReturn(true);
            assertThat(method.invoke(page, rainWorld)).isEqualTo("weather.rain");

            World clearWorld = mock(World.class);
            when(clearWorld.isThundering()).thenReturn(false);
            when(clearWorld.hasStorm()).thenReturn(false);
            assertThat(method.invoke(page, clearWorld)).isEqualTo("weather.clear");
        }
    }

    @Nested
    @DisplayName("createWorldIcon with edge cases")
    class CreateWorldIconTests {

        @Test
        @DisplayName("createWorldIcon should handle invalid icon material")
        void createWorldIconInvalidMaterial() throws Exception {
            Player player = UltiWorldsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());
            WorldListPage page = createPage(player);

            World world = mock(World.class);
            when(world.getName()).thenReturn("world");
            when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
            when(world.getPlayers()).thenReturn(Collections.emptyList());
            when(world.getTime()).thenReturn(6000L);
            when(world.isThundering()).thenReturn(false);
            when(world.hasStorm()).thenReturn(false);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setIcon("INVALID_MATERIAL");
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            Method method = WorldListPage.class.getDeclaredMethod("createWorldIcon", World.class);
            method.setAccessible(true); // NOPMD - test reflection

            Icon icon = (Icon) method.invoke(page, world);
            assertThat(icon).isNotNull();
        }

        @Test
        @DisplayName("createWorldIcon should handle null display name")
        void createWorldIconNullDisplayName() throws Exception {
            Player player = UltiWorldsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());
            WorldListPage page = createPage(player);

            World world = mock(World.class);
            when(world.getName()).thenReturn("world");
            when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
            when(world.getPlayers()).thenReturn(Collections.emptyList());
            when(world.getTime()).thenReturn(6000L);
            when(world.isThundering()).thenReturn(false);
            when(world.hasStorm()).thenReturn(false);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setDisplayName(null);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            Method method = WorldListPage.class.getDeclaredMethod("createWorldIcon", World.class);
            method.setAccessible(true); // NOPMD - test reflection

            Icon icon = (Icon) method.invoke(page, world);
            assertThat(icon).isNotNull();
        }

        @Test
        @DisplayName("createWorldIcon should show protection, locked, and blocked status")
        void createWorldIconProtected() throws Exception {
            Player player = UltiWorldsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());
            WorldListPage page = createPage(player);

            World world = mock(World.class);
            when(world.getName()).thenReturn("world");
            when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
            when(world.getPlayers()).thenReturn(Collections.emptyList());
            when(world.getTime()).thenReturn(6000L);
            when(world.isThundering()).thenReturn(false);
            when(world.hasStorm()).thenReturn(false);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.enableFullProtection();
            settings.setLocked(true);
            settings.setBlocked(true);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            Method method = WorldListPage.class.getDeclaredMethod("createWorldIcon", World.class);
            method.setAccessible(true); // NOPMD - test reflection

            Icon icon = (Icon) method.invoke(page, world);
            assertThat(icon).isNotNull();
        }

        @Test
        @DisplayName("createWorldIcon should handle empty description")
        void createWorldIconEmptyDescription() throws Exception {
            Player player = UltiWorldsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());
            WorldListPage page = createPage(player);

            World world = mock(World.class);
            when(world.getName()).thenReturn("world");
            when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
            when(world.getPlayers()).thenReturn(Collections.emptyList());
            when(world.getTime()).thenReturn(6000L);
            when(world.isThundering()).thenReturn(false);
            when(world.hasStorm()).thenReturn(false);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setDescription("");
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            Method method = WorldListPage.class.getDeclaredMethod("createWorldIcon", World.class);
            method.setAccessible(true); // NOPMD - test reflection

            Icon icon = (Icon) method.invoke(page, world);
            assertThat(icon).isNotNull();
        }

        @Test
        @DisplayName("createWorldIcon should handle null description")
        void createWorldIconNullDescription() throws Exception {
            Player player = UltiWorldsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());
            WorldListPage page = createPage(player);

            World world = mock(World.class);
            when(world.getName()).thenReturn("world");
            when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
            when(world.getPlayers()).thenReturn(Collections.emptyList());
            when(world.getTime()).thenReturn(6000L);
            when(world.isThundering()).thenReturn(false);
            when(world.hasStorm()).thenReturn(false);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("world");
            settings.setDescription(null);
            when(mockWorldService.getOrCreateSettings("world")).thenReturn(settings);

            Method method = WorldListPage.class.getDeclaredMethod("createWorldIcon", World.class);
            method.setAccessible(true); // NOPMD - test reflection

            Icon icon = (Icon) method.invoke(page, world);
            assertThat(icon).isNotNull();
        }
    }
}
