package com.ultikits.plugins.worlds.commands;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;
import com.ultikits.plugins.worlds.config.WorldConfig;
import com.ultikits.plugins.worlds.entity.WorldSettings;
import com.ultikits.plugins.worlds.service.WorldService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.Query;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every world command reads and writes a world's settings under the server's own spelling of the
 * world's name (UltiKits/UltiWorlds#46).
 *
 * <p>{@code CraftServer#getWorld} resolves a typed name ignoring case, but the settings store is
 * keyed by exact string and every listener reads {@code world.getName()}. A command that stored its
 * result under the typed name therefore wrote a row nobody reads, and reported success.
 *
 * <p>The data operator here is a small exact-string store, not a stub that answers every name the
 * same way, so a row written under the wrong spelling is visible as the wrong row.
 */
@DisplayName("World commands store settings under the server's own world name (UltiWorlds#46)")
class WorldCommandSettingsNameCaseTest {

    private static final String SERVER_NAME = "myworld";
    private static final String TYPED_NAME = "MYWORLD";

    private WorldCommand command;
    private WorldService worldService;
    private WorldConfig mockConfig;
    private UltiToolsPlugin mockPlugin;
    private DataOperator<WorldSettings> mockDataOperator;
    private final Map<String, WorldSettings> rows = new HashMap<>();
    private final AtomicReference<Object> lastKey = new AtomicReference<>();
    private World world;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        UltiWorldsTestHelper.setUp();
        mockPlugin = UltiWorldsTestHelper.getMockPlugin();
        mockConfig = UltiWorldsTestHelper.createDefaultConfig();
        mockDataOperator = mock(DataOperator.class);

        Query<WorldSettings> query = mock(Query.class);
        when(mockDataOperator.query()).thenReturn(query);
        when(query.where(anyString())).thenReturn(query);
        when(query.eq(any())).thenAnswer(inv -> {
            lastKey.set(inv.getArgument(0));
            return query;
        });
        when(query.first()).thenAnswer(inv -> rows.get(String.valueOf(lastKey.get())));
        when(query.delete()).thenAnswer(inv -> rows.remove(String.valueOf(lastKey.get())) == null ? 0 : 1);
        doAnswer(inv -> {
            WorldSettings s = inv.getArgument(0);
            rows.put(s.getWorldName(), s);
            return null;
        }).when(mockDataOperator).insert(any(WorldSettings.class));
        when(mockDataOperator.updateCounted(any(WorldSettings.class))).thenAnswer(inv -> {
            WorldSettings s = inv.getArgument(0);
            rows.put(s.getWorldName(), s);
            return 1;
        });

        worldService = new WorldService();
        UltiWorldsTestHelper.setField(worldService, "config", mockConfig);
        UltiWorldsTestHelper.setField(worldService, "dataOperator", mockDataOperator);
        UltiWorldsTestHelper.setField(worldService, "plugin", mockPlugin);

        command = new WorldCommand();
        UltiWorldsTestHelper.setField(command, "worldService", worldService);
        UltiWorldsTestHelper.setField(command, "plugin", mockPlugin);

        world = mock(World.class);
        when(world.getName()).thenReturn(SERVER_NAME);
        when(world.getPlayers()).thenReturn(Collections.<Player>emptyList());
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiWorldsTestHelper.tearDown();
    }

    /** Paper resolves a name ignoring case: both spellings find the one loaded world. */
    private MockedStatic<Bukkit> serverWithTheWorld() {
        MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.getWorld(TYPED_NAME)).thenReturn(world);
        bukkit.when(() -> Bukkit.getWorld(SERVER_NAME)).thenReturn(world);
        bukkit.when(Bukkit::getWorlds).thenReturn(Collections.singletonList(world));
        return bukkit;
    }

    private Player admin() {
        return UltiWorldsTestHelper.createMockPlayer("Admin", UUID.randomUUID());
    }

    private WorldSettings row(String name) {
        WorldSettings settings = WorldSettings.createDefault(name);
        rows.put(name, settings);
        return settings;
    }

    private void assertNothingStoredUnderTheTypedName() {
        assertThat(rows).doesNotContainKey(TYPED_NAME);
    }

    @Test
    @DisplayName("/world set changes the row the listeners read")
    void setWritesTheServersRow() {
        WorldSettings stored = row(SERVER_NAME);
        try (MockedStatic<Bukkit> bukkit = serverWithTheWorld()) {
            command.setWorldOption(admin(), TYPED_NAME, "pvp", "false");
        }

        assertThat(stored.isPvpEnabled()).isFalse();
        assertNothingStoredUnderTheTypedName();
    }

    @Test
    @DisplayName("/world protect changes the row the listeners read")
    void protectWritesTheServersRow() {
        WorldSettings stored = row(SERVER_NAME);
        try (MockedStatic<Bukkit> bukkit = serverWithTheWorld()) {
            command.protectWorld(admin(), TYPED_NAME);
        }

        assertThat(stored.hasProtection()).isTrue();
        assertNothingStoredUnderTheTypedName();
    }

    @Test
    @DisplayName("/world unprotect changes the row the listeners read")
    void unprotectWritesTheServersRow() {
        WorldSettings stored = row(SERVER_NAME);
        stored.enableFullProtection();
        try (MockedStatic<Bukkit> bukkit = serverWithTheWorld()) {
            command.unprotectWorld(admin(), TYPED_NAME);
        }

        assertThat(stored.hasProtection()).isFalse();
        assertNothingStoredUnderTheTypedName();
    }

    @Test
    @DisplayName("/world block changes the row the listeners read")
    void blockWritesTheServersRow() {
        WorldSettings stored = row(SERVER_NAME);
        try (MockedStatic<Bukkit> bukkit = serverWithTheWorld()) {
            command.blockWorld(admin(), TYPED_NAME);
        }

        assertThat(stored.isBlocked()).isTrue();
        assertNothingStoredUnderTheTypedName();
    }

    @Test
    @DisplayName("/world unblock changes the row the listeners read")
    void unblockWritesTheServersRow() {
        WorldSettings stored = row(SERVER_NAME);
        stored.setBlocked(true);
        try (MockedStatic<Bukkit> bukkit = serverWithTheWorld()) {
            command.unblockWorld(admin(), TYPED_NAME);
        }

        assertThat(stored.isBlocked()).isFalse();
        assertNothingStoredUnderTheTypedName();
    }

    @Test
    @DisplayName("/world difficulty changes the row the listeners read")
    void difficultyWritesTheServersRow() {
        WorldSettings stored = row(SERVER_NAME);
        try (MockedStatic<Bukkit> bukkit = serverWithTheWorld()) {
            command.setDifficulty(admin(), TYPED_NAME, "HARD");
        }

        assertThat(stored.getDifficulty()).isEqualTo(Difficulty.HARD.name());
        assertNothingStoredUnderTheTypedName();
    }

    @Test
    @DisplayName("/world postcmd add changes the row the listeners read")
    void postcmdAddWritesTheServersRow() {
        WorldSettings stored = row(SERVER_NAME);
        try (MockedStatic<Bukkit> bukkit = serverWithTheWorld()) {
            command.addPostCmd(admin(), TYPED_NAME, new String[] {"say", "hello"});
        }

        assertThat(stored.getPostTeleportCommands()).isEqualTo("say hello");
        assertNothingStoredUnderTheTypedName();
    }

    @Test
    @DisplayName("/world postcmd clear changes the row the listeners read")
    void postcmdClearWritesTheServersRow() {
        WorldSettings stored = row(SERVER_NAME);
        stored.setPostTeleportCommands("say hello");
        try (MockedStatic<Bukkit> bukkit = serverWithTheWorld()) {
            command.clearPostCmd(admin(), TYPED_NAME);
        }

        assertThat(stored.getPostTeleportCommands()).isNull();
        assertNothingStoredUnderTheTypedName();
    }

    @Test
    @DisplayName("/world postcmd list shows the commands the row the listeners read holds")
    void postcmdListReadsTheServersRow() {
        row(SERVER_NAME).setPostTeleportCommands("say hello");
        Player admin = admin();
        try (MockedStatic<Bukkit> bukkit = serverWithTheWorld()) {
            command.listPostCmd(admin, TYPED_NAME);
        }

        verify(admin).sendMessage("success.post_cmd_list_item");
        verify(admin, never()).sendMessage("success.post_cmd_empty");
        assertNothingStoredUnderTheTypedName();
    }

    @Test
    @DisplayName("a teleport to a blocked world typed in another case is still refused")
    void teleportHonoursTheServersRow() {
        row(SERVER_NAME).setBlocked(true);
        Player visitor = admin();
        when(visitor.hasPermission("ultiworlds.bypass.blocked")).thenReturn(false);
        boolean teleported;
        try (MockedStatic<Bukkit> bukkit = serverWithTheWorld()) {
            teleported = worldService.teleportToWorld(visitor, TYPED_NAME);
        }

        assertThat(teleported).isFalse();
        verify(visitor, never()).teleport(any(org.bukkit.Location.class));
        assertNothingStoredUnderTheTypedName();
    }

    @Test
    @DisplayName("/world delete removes the row the listeners read, not a row under the typed name")
    void deleteRemovesTheServersRow() throws IOException {
        row(SERVER_NAME);
        File container = Files.createTempDirectory("p17wfu46").toFile();
        try (MockedStatic<Bukkit> bukkit = serverWithTheWorld()) {
            bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
            bukkit.when(() -> Bukkit.unloadWorld(any(World.class), any(Boolean.class))).thenReturn(true);
            when(mockConfig.getDefaultWorld()).thenReturn("lobby");
            when(mockConfig.getProtectedWorlds()).thenReturn(Collections.<String>emptyList());
            World lobby = mock(World.class);
            when(lobby.getSpawnLocation()).thenReturn(mock(org.bukkit.Location.class));
            bukkit.when(() -> Bukkit.getWorld("lobby")).thenReturn(lobby);

            worldService.deleteWorld(TYPED_NAME);
        } finally {
            container.delete();
        }

        assertThat(rows).doesNotContainKey(SERVER_NAME);
    }
}
