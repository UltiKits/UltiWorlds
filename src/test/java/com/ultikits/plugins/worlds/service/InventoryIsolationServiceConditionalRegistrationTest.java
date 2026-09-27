package com.ultikits.plugins.worlds.service;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.context.ConditionalRegistrationEvaluator;
import com.ultikits.ultitools.context.SimpleContainer;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Falsification test for UltiWorlds#10: whether the inventory-isolation service exists at
 * runtime matches the configured flag at cold start, and whether a later change to that flag is
 * reported as drift for this service and this key after a reload.
 * <p>
 * <b>Cold start (re-derivation):</b> the annotation's sense ({@code negate=false}), the
 * configured path (identical to {@code WorldConfig}'s own {@code @ConfigEntry} key), and the
 * framework's evaluator (proven correct separately) are all independently confirmed correct by
 * reading source. {@link #coldStartEnabledTrueRegistersTheService(Path)} and {@link
 * #coldStartEnabledFalseDoesNotRegisterTheService(Path)} exercise the real gate directly (the
 * framework's sole {@code @ConditionalOnConfig} gate, {@code
 * ConditionalRegistrationEvaluator.shouldRegister}, whose only {@code src/main} caller is
 * {@code ComponentScanner.shouldRegister}) and are green before any change -- recorded here as
 * evidence rather than forced red.
 * <p>
 * <b>Reload drift:</b> the framework evaluates {@code @ConditionalOnConfig} once, at component
 * scan; a reload never registers or unregisters anything and only reports drift via
 * {@code ConditionalRegistrationEvaluator.reportDrift}.
 * {@link #driftReportedForIsolationFlagFlippedOn(Path)} and
 * {@link #driftReportedForIsolationFlagFlippedOff(Path)} call the real evaluator, without static
 * mocks, and assert the drift message names this service, {@code config/worlds.yml} and
 * {@code world_isolation.enabled}, with a no-edit control first so neither can pass vacuously.
 * That {@code UltiToolsPlugin#reloadSelf()} (final since UltiTools 6.3.0, so this module can no
 * longer override it) calls {@code reportDrift} is asserted by the framework's own
 * {@code ConditionalRegistrationEvaluatorDriftTest}; that UltiWorlds declares no override is
 * asserted by {@code UltiWorldsTest$LifecycleContract} (UltiWorlds#27).
 */
@DisplayName("InventoryIsolationService conditional-registration wiring")
class InventoryIsolationServiceConditionalRegistrationTest {

    /**
     * Every plugin whose scan-time decision a test recorded; released after each test so the
     * evaluator's static record does not accumulate across tests.
     */
    private final List<UltiToolsPlugin> recordedPlugins = new ArrayList<>();

    @AfterEach
    void releaseRecordedDecisions() {
        for (UltiToolsPlugin plugin : recordedPlugins) {
            ConditionalRegistrationEvaluator.clear(plugin);
        }
        recordedPlugins.clear();
    }

    @Test
    @DisplayName("cold start, flag enabled=true: the service IS registered (documented, non-inverted @ConditionalOnConfig sense)")
    void coldStartEnabledTrueRegistersTheService(@TempDir Path tempDir) throws IOException {
        writeIsolationConfig(tempDir, true);
        UltiToolsPlugin plugin = pluginAt(tempDir);
        SimpleContainer container = containerFor(plugin);

        boolean decision = ConditionalRegistrationEvaluator.shouldRegister(
                InventoryIsolationService.class, container);

        assertThat(decision)
                .as("world_isolation.enabled=true at scan time must register the service")
                .isTrue();
    }

    @Test
    @DisplayName("cold start, flag enabled=false: the service is NOT registered (documented, non-inverted @ConditionalOnConfig sense)")
    void coldStartEnabledFalseDoesNotRegisterTheService(@TempDir Path tempDir) throws IOException {
        writeIsolationConfig(tempDir, false);
        UltiToolsPlugin plugin = pluginAt(tempDir);
        SimpleContainer container = containerFor(plugin);

        boolean decision = ConditionalRegistrationEvaluator.shouldRegister(
                InventoryIsolationService.class, container);

        assertThat(decision)
                .as("world_isolation.enabled=false at scan time must NOT register the service")
                .isFalse();
    }

    @Test
    @DisplayName("flag flipped false -> true after scan: drift names InventoryIsolationService, config/worlds.yml and world_isolation.enabled, now enabled")
    void driftReportedForIsolationFlagFlippedOn(@TempDir Path tempDir) throws IOException {
        writeIsolationConfig(tempDir, false);
        UltiToolsPlugin plugin = pluginAt(tempDir);
        SimpleContainer container = containerFor(plugin);
        when(container.hasConstructedInstanceOfType(InventoryIsolationService.class)).thenReturn(false);
        assertThat(ConditionalRegistrationEvaluator.shouldRegister(
                InventoryIsolationService.class, container)).isFalse();

        // Control: no edit, no drift -- so the assertion below cannot pass vacuously.
        assertThat(ConditionalRegistrationEvaluator.reportDrift(plugin)).isEmpty();

        writeIsolationConfig(tempDir, true);
        List<String> messages = ConditionalRegistrationEvaluator.reportDrift(plugin);

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0))
                .contains(InventoryIsolationService.class.getName())
                .contains("(config/worlds.yml -> world_isolation.enabled)")
                .contains("now evaluates to enabled, but the component was not registered at startup")
                .contains("a restart is required to create the component.");
    }

    @Test
    @DisplayName("flag flipped true -> false after scan: drift names InventoryIsolationService, config/worlds.yml and world_isolation.enabled, now disabled")
    void driftReportedForIsolationFlagFlippedOff(@TempDir Path tempDir) throws IOException {
        writeIsolationConfig(tempDir, true);
        UltiToolsPlugin plugin = pluginAt(tempDir);
        SimpleContainer container = containerFor(plugin);
        when(container.hasConstructedInstanceOfType(InventoryIsolationService.class)).thenReturn(true);
        assertThat(ConditionalRegistrationEvaluator.shouldRegister(
                InventoryIsolationService.class, container)).isTrue();

        // Control: no edit, no drift -- so the assertion below cannot pass vacuously.
        assertThat(ConditionalRegistrationEvaluator.reportDrift(plugin)).isEmpty();

        writeIsolationConfig(tempDir, false);
        List<String> messages = ConditionalRegistrationEvaluator.reportDrift(plugin);

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0))
                .contains(InventoryIsolationService.class.getName())
                .contains("(config/worlds.yml -> world_isolation.enabled)")
                .contains("now evaluates to disabled, but the component is already registered")
                .contains("a restart is required to remove the component.");
    }

    private UltiToolsPlugin pluginAt(Path tempDir) {
        UltiToolsPlugin plugin = mock(UltiToolsPlugin.class);
        when(plugin.getResourceFolderPath()).thenReturn(tempDir.toString());
        recordedPlugins.add(plugin);
        return plugin;
    }

    private SimpleContainer containerFor(UltiToolsPlugin plugin) {
        SimpleContainer container = mock(SimpleContainer.class);
        when(container.getBean(UltiToolsPlugin.class)).thenReturn(plugin);
        when(plugin.getContext()).thenReturn(container);
        return container;
    }

    private void writeIsolationConfig(Path tempDir, boolean enabled) throws IOException {
        File configDir = new File(tempDir.toFile(), "config");
        configDir.mkdirs();
        File file = new File(configDir, "worlds.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("world_isolation.enabled", enabled);
        yaml.save(file);
    }
}
