package com.ultikits.plugins.worlds.service;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;
import com.ultikits.plugins.worlds.entity.WorldSettings;
import com.ultikits.plugins.worlds.testsupport.SharedSqliteDatabase;
import com.ultikits.ultitools.exceptions.ErrorCode;
import com.ultikits.ultitools.exceptions.DataAccessException;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.Query;
import com.ultikits.ultitools.interfaces.impl.data.json.SimpleJsonDataOperator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Deterministic read-A, read-B, insert-B, insert-A interleaving, without threads or a server (#57). */
class WorldSettingsCreationRaceTest {
    private static final String WORLD = "shared_world";

    enum Backend { SQLITE, JSON }

    @TempDir
    Path dir;

    private DataOperator<WorldSettings> table(Backend backend) throws Exception {
        return backend == Backend.SQLITE
            ? SharedSqliteDatabase.in(dir).openAs(WorldSettings.class)
            : new SimpleJsonDataOperator<>(dir.resolve("json").toString(), WorldSettings.class);
    }

    private WorldService service(DataOperator<WorldSettings> operator) throws Exception {
        WorldService service = new WorldService();
        // No plugin, Bukkit singleton or MockBukkit bootstrap: these default rows have no difficulty.
        UltiWorldsTestHelper.setField(service, "dataOperator", operator);
        return service;
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void twoAbsentReadsUseOneStoredWinner(Backend backend) throws Exception {
        DataOperator<WorldSettings> table = table(backend);
        WorldService serverB = service(table);
        @SuppressWarnings("unchecked")
        DataOperator<WorldSettings> pausedA = mock(DataOperator.class, delegatesTo(table));
        WorldSettings[] winner = new WorldSettings[1];
        doAnswer(call -> {
            // A has already read absent. The store is still empty, so B also reads absent now.
            assertThat(table.getAll()).isEmpty();
            winner[0] = serverB.getOrCreateSettings(WORLD);
            winner[0].setDescription("the stored winner, not A's defaults");
            table.update(winner[0]);
            table.insert(call.getArgument(0));
            return null;
        }).when(pausedA).insert(any(WorldSettings.class));
        WorldService serverA = service(pausedA);

        WorldSettings result = serverA.getOrCreateSettings(WORLD);

        assertThat(table.getAll()).hasSize(1);
        assertThat(result.getId()).isEqualTo(winner[0].getId());
        assertThat(result.getDescription()).isEqualTo(winner[0].getDescription());
        assertThat(serverA.getOrCreateSettings(WORLD)).isSameAs(result);
        assertThat(serverB.getOrCreateSettings(WORLD).getId()).isEqualTo(result.getId());
        assertThat(table.getById(result.getId()).getDescription()).isEqualTo(result.getDescription());
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void existingRandomIdAndValuesAreReused(Backend backend) throws Exception {
        DataOperator<WorldSettings> table = table(backend);
        WorldSettings old = WorldSettings.createDefault(WORLD);
        String oldId = UUID.randomUUID().toString();
        old.setId(oldId);
        old.setDescription("existing operator settings");
        old.setBlocked(true);
        table.insert(old);

        WorldSettings result = service(table).getOrCreateSettings(WORLD);

        assertThat(result.getId()).isEqualTo(oldId);
        assertThat(result.getDescription()).isEqualTo(old.getDescription());
        assertThat(result.isBlocked()).isTrue();
        assertThat(table.getAll()).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void differentWorldsKeepIndependentRows(Backend backend) throws Exception {
        DataOperator<WorldSettings> table = table(backend);
        WorldService server = service(table);
        WorldSettings first = server.getOrCreateSettings(WORLD);
        WorldSettings second = server.getOrCreateSettings("another_world");

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(first.getId()).matches("[a-f0-9-]{36}");
        assertThat(table.getAll()).hasSize(2);
        assertThat(service(table).getOrCreateSettings(WORLD).getId()).isEqualTo(first.getId());
        assertThat(service(table).getOrCreateSettings("another_world").getId()).isEqualTo(second.getId());
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void preExistingDuplicateRowsAreNotMigratedOrDeleted(Backend backend) throws Exception {
        DataOperator<WorldSettings> table = table(backend);
        WorldSettings first = WorldSettings.createDefault(WORLD);
        WorldSettings second = WorldSettings.createDefault(WORLD);
        table.insert(first);
        table.insert(second);

        WorldSettings result = service(table).getOrCreateSettings(WORLD);

        assertThat(result.getId()).isIn(first.getId(), second.getId());
        assertThat(table.getAll()).extracting(WorldSettings::getId)
            .containsExactlyInAnyOrder(first.getId(), second.getId());
    }

    @SuppressWarnings("unchecked")
    private DataOperator<WorldSettings> absentTable() {
        DataOperator<WorldSettings> table = mock(DataOperator.class);
        Query<WorldSettings> query = mock(Query.class, RETURNS_SELF);
        when(table.query()).thenReturn(query);
        return table;
    }

    private DataAccessException insertFailure() {
        return new DataAccessException(ErrorCode.DATA_OPERATION_FAILED, "insert failed");
    }

    @Test
    void failedInsertWithoutWinnerRethrowsOriginalAndDoesNotCache() throws Exception {
        DataOperator<WorldSettings> table = absentTable();
        DataAccessException failure = insertFailure();
        doThrow(failure).when(table).insert(any(WorldSettings.class));
        WorldService server = service(table);

        assertThatThrownBy(() -> server.getOrCreateSettings(WORLD)).isSameAs(failure);
        assertThatThrownBy(() -> server.getOrCreateSettings(WORLD)).isSameAs(failure);
        verify(table, times(2)).insert(any(WorldSettings.class));
    }

    @Test
    void failedInsertWithDifferentWorldAtTheIdIsNotRecovered() throws Exception {
        DataOperator<WorldSettings> table = absentTable();
        DataAccessException failure = insertFailure();
        when(table.getById(any())).thenReturn(WorldSettings.createDefault("unrelated_world"));
        doThrow(failure).when(table).insert(any(WorldSettings.class));

        assertThatThrownBy(() -> service(table).getOrCreateSettings(WORLD)).isSameAs(failure);
    }

    @Test
    void failedWinnerReadPreservesOriginalInsertFailure() throws Exception {
        DataOperator<WorldSettings> table = absentTable();
        DataAccessException failure = insertFailure();
        doThrow(failure).when(table).insert(any(WorldSettings.class));
        when(table.getById(any())).thenThrow(new DataAccessException(
            ErrorCode.DATA_OPERATION_FAILED, "read also failed"));

        assertThatThrownBy(() -> service(table).getOrCreateSettings(WORLD)).isSameAs(failure);
    }

    @Test
    void nonStorageInsertFailureIsNotRecovered() throws Exception {
        DataOperator<WorldSettings> table = absentTable();
        IllegalStateException failure = new IllegalStateException("onCreate failed");
        doThrow(failure).when(table).insert(any(WorldSettings.class));

        assertThatThrownBy(() -> service(table).getOrCreateSettings(WORLD)).isSameAs(failure);
        verify(table, never()).getById(any());
    }
}
