package com.ultikits.plugins.worlds.service;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;
import com.ultikits.plugins.worlds.entity.WorldSettings;
import com.ultikits.ultitools.annotations.Column;
import com.ultikits.ultitools.entities.WhereCondition;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guard for the compare-and-set of a world-settings change (UltiKits/UltiWorlds#55): {@code updateIf}
 * writes every column, so the condition must cover every column except the documented exceptions, or a
 * change would silently write back another server's change to an uncovered column. A column added to
 * {@link WorldSettings} later without a condition fails here.
 */
@DisplayName("A world-settings change is conditioned on every stored column but the documented ones (UltiWorlds#55 guard)")
class WorldSettingsConditionColumnsTest {

    /** Left out on purpose, with the reason in {@code WorldService#valuesAsRead}'s javadoc. */
    private static final Set<String> NOT_COMPARED = new HashSet<>(Arrays.asList("id", "spawn_yaw", "spawn_pitch"));

    private static Set<String> storedColumns() {
        Set<String> columns = new HashSet<>();
        for (Class<?> type = WorldSettings.class; type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                Column column = field.getAnnotation(Column.class);
                if (column != null) {
                    columns.add(column.value());
                }
            }
        }
        return columns;
    }

    private static Map<String, Object> conditions(WorldSettings row) {
        Map<String, Object> byColumn = new LinkedHashMap<>();
        for (WhereCondition condition : WorldService.valuesAsRead(row)) {
            assertThat(byColumn.put(condition.getColumn(), condition.getValue()))
                    .as("one condition per column: %s", condition.getColumn()).isNull();
        }
        return byColumn;
    }

    @Test
    @DisplayName("Every stored column but id, spawn_yaw and spawn_pitch is compared, with the value read")
    void everyColumnButTheDocumentedOnes() {
        WorldSettings row = UltiWorldsTestHelper.createSampleWorldSettings("guard_world");
        row.setDifficulty("HARD");
        row.setPostTeleportCommands("say hi");

        Map<String, Object> byColumn = conditions(row);

        Set<String> expected = storedColumns();
        assertThat(expected).as("control: the entity's columns were found").contains("pvp_enabled", "spawn_yaw");
        expected.removeAll(NOT_COMPARED);
        assertThat(byColumn.keySet()).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(byColumn.get("pvp_enabled")).isEqualTo(true);
        assertThat(byColumn.get("difficulty")).isEqualTo("HARD");
        assertThat(byColumn.get("spawn_y")).isEqualTo(64.0);
        assertThat(byColumn.get("created_at")).isEqualTo(row.getCreatedAt());
    }

    @Test
    @DisplayName("A column read as NULL is left out, since updateIf cannot compare with null")
    void nullColumnsAreLeftOut() {
        WorldSettings row = UltiWorldsTestHelper.createSampleWorldSettings("guard_world");

        Map<String, Object> byColumn = conditions(row);

        assertThat(row.getDifficulty()).as("precondition").isNull();
        assertThat(byColumn).doesNotContainKeys("difficulty", "post_teleport_commands");
        assertThat(byColumn.values()).doesNotContainNull();
    }
}
