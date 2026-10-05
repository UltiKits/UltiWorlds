package com.ultikits.plugins.worlds.commands;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;
import com.ultikits.plugins.worlds.config.WorldConfig;
import com.ultikits.plugins.worlds.entity.WorldSettings;
import com.ultikits.plugins.worlds.i18n.CatalogueText;
import com.ultikits.plugins.worlds.service.WorldService;
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
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code /world list} leaves out a world the operator marked hidden, exactly as the world list
 * window does (UltiKits/UltiWorlds#48). The settings come from a real {@link WorldService}, so the
 * hidden flag is read where the window reads it.
 */
@DisplayName("/world list hides worlds marked hidden (UltiWorlds#48)")
class WorldCommandListHiddenTest {

    private WorldCommand command;
    private UltiToolsPlugin mockPlugin;
    private final Map<String, WorldSettings> rows = new HashMap<>();
    private final AtomicReference<Object> lastKey = new AtomicReference<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        UltiWorldsTestHelper.setUp();
        mockPlugin = UltiWorldsTestHelper.getMockPlugin();
        when(mockPlugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        WorldConfig config = UltiWorldsTestHelper.createDefaultConfig();
        DataOperator<WorldSettings> dataOperator = mock(DataOperator.class);
        Query<WorldSettings> query = mock(Query.class);
        when(dataOperator.query()).thenReturn(query);
        when(query.where(anyString())).thenReturn(query);
        when(query.eq(any())).thenAnswer(inv -> {
            lastKey.set(inv.getArgument(0));
            return query;
        });
        when(query.first()).thenAnswer(inv -> rows.get(String.valueOf(lastKey.get())));

        WorldService worldService = new WorldService();
        UltiWorldsTestHelper.setField(worldService, "config", config);
        UltiWorldsTestHelper.setField(worldService, "dataOperator", dataOperator);
        UltiWorldsTestHelper.setField(worldService, "plugin", mockPlugin);

        command = new WorldCommand();
        UltiWorldsTestHelper.setField(command, "worldService", worldService);
        UltiWorldsTestHelper.setField(command, "plugin", mockPlugin);
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiWorldsTestHelper.tearDown();
    }

    private World world(String name) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        when(world.getPlayers()).thenReturn(Collections.<Player>emptyList());
        return world;
    }

    @Test
    @DisplayName("/world list leaves out a world marked hidden, as the world list window does (UltiWorlds#48)")
    void listHidesWorldsMarkedHidden() {
        World visible = world("myworld");
        World secret = world("secretworld");
        rows.put("myworld", WorldSettings.createDefault("myworld"));
        WorldSettings hidden = WorldSettings.createDefault("secretworld");
        hidden.setHidden(true);
        rows.put("secretworld", hidden);
        Player player = UltiWorldsTestHelper.createMockPlayer("Player", UUID.randomUUID());
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(Arrays.asList(visible, secret));

            command.listWorlds(player);
        }

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(player, atLeastOnce()).sendMessage(sent.capture());
        assertThat(sent.getAllValues())
            .anyMatch(line -> line.contains("(1 total)"))
            .anyMatch(line -> line.contains("myworld"))
            .noneMatch(line -> line.contains("secretworld"));
    }
}
