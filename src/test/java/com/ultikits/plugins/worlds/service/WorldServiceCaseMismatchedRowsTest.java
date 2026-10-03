package com.ultikits.plugins.worlds.service;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;
import com.ultikits.plugins.worlds.config.WorldConfig;
import com.ultikits.plugins.worlds.entity.WorldSettings;
import com.ultikits.plugins.worlds.i18n.CatalogueText;
import com.ultikits.plugins.worlds.util.Placeholders;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.Query;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Settings rows stored under another spelling of a loaded world's name are listed at start-up and
 * are not merged (UltiKits/UltiWorlds#46, maintainer decision of 2026-09-29: "use the server's own
 * world name everywhere; do not merge old rows; list them at start-up").
 */
@DisplayName("World settings rows that match a world only ignoring case are listed at start-up (UltiWorlds#46)")
class WorldServiceCaseMismatchedRowsTest {

    private WorldService worldService;
    private UltiToolsPlugin mockPlugin;
    private WorldConfig mockConfig;
    private DataOperator<WorldSettings> mockDataOperator;
    private Query<WorldSettings> mockQuery;
    private final Map<String, WorldSettings> rows = new LinkedHashMap<>();
    private final AtomicReference<Object> lastKey = new AtomicReference<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        UltiWorldsTestHelper.setUp();
        mockPlugin = UltiWorldsTestHelper.getMockPlugin();
        when(mockPlugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        mockConfig = UltiWorldsTestHelper.createDefaultConfig();
        when(mockConfig.getLoadWorldsOnStart()).thenReturn(Collections.<String>emptyList());
        mockDataOperator = mock(DataOperator.class);

        Query<WorldSettings> query = mock(Query.class);
        mockQuery = query;
        when(mockDataOperator.query()).thenReturn(query);
        when(query.where(anyString())).thenReturn(query);
        when(query.eq(any())).thenAnswer(inv -> {
            lastKey.set(inv.getArgument(0));
            return query;
        });
        when(query.first()).thenAnswer(inv -> rows.get(String.valueOf(lastKey.get())));
        when(mockDataOperator.getAll()).thenAnswer(inv -> new ArrayList<>(rows.values()));
        doAnswer(inv -> {
            WorldSettings s = inv.getArgument(0);
            rows.put(s.getWorldName(), s);
            return null;
        }).when(mockDataOperator).insert(any(WorldSettings.class));

        worldService = new WorldService();
        UltiWorldsTestHelper.setField(worldService, "config", mockConfig);
        UltiWorldsTestHelper.setField(worldService, "plugin", mockPlugin);
        when(mockPlugin.getDataOperator(WorldSettings.class)).thenReturn(mockDataOperator);
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiWorldsTestHelper.tearDown();
    }

    private World world(String name) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        return world;
    }

    private String expectedWarning(String row, String world) {
        return Placeholders.fill(CatalogueText.text("en", "log.settings_row_case_mismatch"),
            "{ROW}", row, "{WORLD}", world);
    }

    @Test
    @DisplayName("each such row gets one warning naming the row and the world, and nothing is merged or removed")
    void listsEachMismatchedRowOnce() {
        rows.put("MyWorld", WorldSettings.createDefault("MyWorld"));
        rows.put("MYWORLD", WorldSettings.createDefault("MYWORLD"));
        rows.put("elsewhere", WorldSettings.createDefault("elsewhere"));
        List<World> loaded = Arrays.asList(world("myworld"), world("nether"));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(loaded);

            worldService.init();
        }

        verify(UltiWorldsTestHelper.getMockLogger()).warn(expectedWarning("MyWorld", "myworld"));
        verify(UltiWorldsTestHelper.getMockLogger()).warn(expectedWarning("MYWORLD", "myworld"));
        verify(UltiWorldsTestHelper.getMockLogger(), times(2)).warn(
            org.mockito.ArgumentMatchers.contains("only when letter case is ignored"));
        // Not merged, not deleted: the three old rows are exactly as they were.
        assertThat(rows).containsKeys("MyWorld", "MYWORLD", "elsewhere");
        verify(mockDataOperator, never()).del(any());
        verify(mockDataOperator, never()).delById(any());
        verify(mockDataOperator, never()).updateCounted(any(WorldSettings.class));
        verify(mockDataOperator, never()).insertAll(any());
        verify(mockQuery, never()).delete();
    }

    @Test
    @DisplayName("a row that is exactly a loaded world's name, and a row of no loaded world, are not listed")
    void staysQuietWhenNothingMismatches() {
        rows.put("myworld", WorldSettings.createDefault("myworld"));
        rows.put("elsewhere", WorldSettings.createDefault("elsewhere"));
        List<World> loaded = Collections.singletonList(world("myworld"));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(loaded);

            worldService.init();
        }

        verify(UltiWorldsTestHelper.getMockLogger(), never()).warn(
            org.mockito.ArgumentMatchers.contains("only when letter case is ignored"));
    }
}
