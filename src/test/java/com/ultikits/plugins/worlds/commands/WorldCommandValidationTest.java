package com.ultikits.plugins.worlds.commands;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;
import com.ultikits.plugins.worlds.config.WorldConfig;
import com.ultikits.plugins.worlds.entity.WorldSettings;
import com.ultikits.plugins.worlds.service.WorldService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Validation-parity tests for the four {@code /world} subcommands that did not check the name they
 * were given the way the module's other nine handlers already do: {@code delete} and the three
 * {@code postcmd} handlers.
 * <p>
 * Deliberately flat (no {@code @Nested} groups): Surefire reports one "Tests run" summary line per
 * nested class, and this module's own gate reads the first such line in the log to confirm total
 * coverage — a flat class keeps that line accurate for the whole class.
 *
 * @author wisdomme
 * @version 2.0.0
 */
@DisplayName("WorldCommand Validation Tests")
class WorldCommandValidationTest {

    private WorldCommand command;
    private WorldService mockWorldService;
    private WorldConfig mockConfig;
    private UltiToolsPlugin mockPlugin;

    @BeforeEach
    void setUp() throws Exception {
        UltiWorldsTestHelper.setUp();
        mockPlugin = UltiWorldsTestHelper.getMockPlugin();

        command = new WorldCommand();
        mockWorldService = mock(WorldService.class);
        mockConfig = UltiWorldsTestHelper.createDefaultConfig();

        UltiWorldsTestHelper.setField(command, "worldService", mockWorldService);
        UltiWorldsTestHelper.setField(command, "plugin", mockPlugin);

        when(mockWorldService.getConfig()).thenReturn(mockConfig);
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiWorldsTestHelper.tearDown();
    }

    @Test
    @DisplayName("deleteRejectsANameThatIsNotALoadedWorld")
    void deleteRejectsANameThatIsNotALoadedWorld() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("ghost_world")).thenReturn(null);

            Player player = UltiWorldsTestHelper.createMockPlayer("Admin", UUID.randomUUID());

            command.deleteWorld(player, "ghost_world");

            verify(mockWorldService, never()).deleteWorld(anyString());
            verify(player).sendMessage(anyString());
        }
    }

    @ParameterizedTest(name = "deleteRejectsANameOutsideTheLegalNameForm[{0}]")
    @ValueSource(strings = {"world/evil", "world.bad"})
    @DisplayName("deleteRejectsANameOutsideTheLegalNameForm")
    void deleteRejectsANameOutsideTheLegalNameForm(String illegalName) {
        Player player = UltiWorldsTestHelper.createMockPlayer("Admin", UUID.randomUUID());

        command.deleteWorld(player, illegalName);

        verify(mockWorldService, never()).deleteWorld(anyString());
        verify(player).sendMessage(anyString());
    }

    @ParameterizedTest(name = "deleteRejectsAnEmptyOrBlankName[{0}]")
    @ValueSource(strings = {"", "   "})
    @DisplayName("deleteRejectsAnEmptyOrBlankName")
    void deleteRejectsAnEmptyOrBlankName(String blankName) {
        Player player = UltiWorldsTestHelper.createMockPlayer("Admin", UUID.randomUUID());

        command.deleteWorld(player, blankName);

        verify(mockWorldService, never()).deleteWorld(anyString());
        verify(player).sendMessage(anyString());
    }

    @Test
    @DisplayName("postCommandAddRejectsANameThatIsNotALoadedWorld")
    void postCommandAddRejectsANameThatIsNotALoadedWorld() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("ghost_world")).thenReturn(null);

            Player player = UltiWorldsTestHelper.createMockPlayer("Admin", UUID.randomUUID());

            command.addPostCmd(player, "ghost_world", "say hello");

            verify(mockWorldService, never()).getOrCreateSettings(anyString());
            verify(player).sendMessage(anyString());
        }
    }

    @Test
    @DisplayName("postCommandAddDoesNotPersistSettingsForARejectedName")
    void postCommandAddDoesNotPersistSettingsForARejectedName() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("ghost_world")).thenReturn(null);

            Player player = UltiWorldsTestHelper.createMockPlayer("Admin", UUID.randomUUID());

            command.addPostCmd(player, "ghost_world", "say hello");

            // The persistence-catching assertion: a check placed one line too late (after the
            // settings lookup) would still have called getOrCreateSettings/updateSettings here.
            verify(mockWorldService, never()).getOrCreateSettings(anyString());
            verify(mockWorldService, never()).updateSettings(any());
        }
    }

    @Test
    @DisplayName("postCommandListRejectsANameThatIsNotALoadedWorld")
    void postCommandListRejectsANameThatIsNotALoadedWorld() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("ghost_world")).thenReturn(null);

            Player player = UltiWorldsTestHelper.createMockPlayer("Admin", UUID.randomUUID());

            command.listPostCmd(player, "ghost_world");

            verify(mockWorldService, never()).getOrCreateSettings(anyString());
            verify(player).sendMessage(anyString());
        }
    }

    @Test
    @DisplayName("postCommandClearRejectsANameThatIsNotALoadedWorld")
    void postCommandClearRejectsANameThatIsNotALoadedWorld() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("ghost_world")).thenReturn(null);

            Player player = UltiWorldsTestHelper.createMockPlayer("Admin", UUID.randomUUID());

            command.clearPostCmd(player, "ghost_world");

            verify(mockWorldService, never()).getOrCreateSettings(anyString());
            verify(player).sendMessage(anyString());
        }
    }

    @Test
    @DisplayName("aLoadedWorldIsStillAccepted")
    void aLoadedWorldIsStillAccepted() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            World world = mock(World.class);
            bukkit.when(() -> Bukkit.getWorld("live_world")).thenReturn(world);
            when(mockConfig.getDefaultWorld()).thenReturn("other_world");
            when(mockWorldService.deleteWorld("live_world")).thenReturn(true);

            WorldSettings settings = UltiWorldsTestHelper.createSampleWorldSettings("live_world");
            when(mockWorldService.getOrCreateSettings("live_world")).thenReturn(settings);

            Player player = UltiWorldsTestHelper.createMockPlayer("Admin", UUID.randomUUID());

            command.deleteWorld(player, "live_world");
            verify(mockWorldService).deleteWorld("live_world");

            command.addPostCmd(player, "live_world", "say hi");
            command.listPostCmd(player, "live_world");
            command.clearPostCmd(player, "live_world");

            verify(mockWorldService, atLeast(3)).getOrCreateSettings("live_world");
        }
    }
}
