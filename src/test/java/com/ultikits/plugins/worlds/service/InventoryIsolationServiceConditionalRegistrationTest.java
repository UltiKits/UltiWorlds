package com.ultikits.plugins.worlds.service;

import com.ultikits.plugins.worlds.UltiWorlds;
import com.ultikits.ultitools.UltiTools;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.context.ConditionalRegistrationEvaluator;
import com.ultikits.ultitools.context.SimpleContainer;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import com.ultikits.ultitools.manager.ConfigManager;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * Falsification test for FIX-04 (UltiWorlds#10): whether the inventory-isolation service exists
 * at runtime matches the configured flag, in both directions and at both entry conditions --
 * cold start (the framework's real, sole {@code @ConditionalOnConfig} gate,
 * {@code ConditionalRegistrationEvaluator.shouldRegister}, the only {@code src/main} caller of
 * which is {@code ComponentScanner.shouldRegister}), and after {@code ul reload} (the
 * framework's documented evaluate-once-at-scan semantics: a reload never re-registers or
 * unregisters anything, it only reports drift via
 * {@code ConditionalRegistrationEvaluator.reportDrift}).
 * <p>
 * <b>Cold start (re-derivation, see {@code 13-LEDGER-UltiWorlds.md}):</b> the annotation's sense
 * ({@code negate=false}), the configured path (identical to {@code WorldConfig}'s own
 * {@code @ConfigEntry} key), and the framework's evaluator (proven correct elsewhere in the same
 * measurement session per 13-CONTEXT.md) are all independently confirmed correct by reading
 * source. {@link #coldStartEnabledTrueRegistersTheService()} and
 * {@link #coldStartEnabledFalseDoesNotRegisterTheService()} exercise the real gate directly and
 * are green before any change -- recorded here as evidence rather than forced red, per
 * {@code 13-RECONFIRMATION.md}'s "Rule for the fan-out."
 * <p>
 * <b>Reload (the actual defect):</b> {@link UltiWorlds#reloadSelf()} overrides
 * {@code UltiToolsPlugin.reloadSelf()} without calling {@code super.reloadSelf()}, so the
 * framework's own drift-reporting mechanism -- the ONLY visible signal an operator gets that a
 * {@code @ConditionalOnConfig} flag flipped without a restart -- is never reached, for every
 * conditional class in this module, not only this one. {@link #reloadReportsDriftWhenFlagFlipsOn()}
 * and {@link #reloadReportsDriftWhenFlagFlipsOff()} are red while that call is missing and green
 * once {@code UltiWorlds.reloadSelf()} delegates to it.
 */
@DisplayName("InventoryIsolationService conditional-registration wiring")
class InventoryIsolationServiceConditionalRegistrationTest {

    @Test
    @DisplayName("cold start, flag enabled=true: the service IS registered (documented, non-inverted @ConditionalOnConfig sense)")
    void coldStartEnabledTrueRegistersTheService(@TempDir Path tempDir) throws IOException {
        writeIsolationConfig(tempDir, true);
        UltiToolsPlugin plugin = mock(UltiToolsPlugin.class);
        when(plugin.getResourceFolderPath()).thenReturn(tempDir.toString());
        SimpleContainer container = mock(SimpleContainer.class);
        when(container.getBean(UltiToolsPlugin.class)).thenReturn(plugin);

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
        UltiToolsPlugin plugin = mock(UltiToolsPlugin.class);
        when(plugin.getResourceFolderPath()).thenReturn(tempDir.toString());
        SimpleContainer container = mock(SimpleContainer.class);
        when(container.getBean(UltiToolsPlugin.class)).thenReturn(plugin);

        boolean decision = ConditionalRegistrationEvaluator.shouldRegister(
                InventoryIsolationService.class, container);

        assertThat(decision)
                .as("world_isolation.enabled=false at scan time must NOT register the service")
                .isFalse();
    }

    @Test
    @DisplayName("flag flipped on and the module reloaded: drift is reported -- documented evaluate-once-at-scan semantics, not a hoped-for re-registration")
    void reloadReportsDriftWhenFlagFlipsOn(@TempDir Path tempDir) throws Exception {
        assertDriftReportedAfterReload(tempDir, false, true);
    }

    @Test
    @DisplayName("flag flipped off and the module reloaded: drift is reported -- documented evaluate-once-at-scan semantics, not a hoped-for re-registration")
    void reloadReportsDriftWhenFlagFlipsOff(@TempDir Path tempDir) throws Exception {
        assertDriftReportedAfterReload(tempDir, true, false);
    }

    /**
     * Records a cold-start decision for {@code initialEnabled}, flips the file on disk to
     * {@code flippedEnabled}, then calls the real {@link UltiWorlds#reloadSelf()} and asserts it
     * invokes {@link ConditionalRegistrationEvaluator#reportDrift(UltiToolsPlugin)} exactly once
     * -- the framework's documented reload contract for this annotation. Per that same contract,
     * this method never asserts that the service's actual registration changes: a reload cannot
     * rebuild the container, only report that it would decide differently now.
     */
    private void assertDriftReportedAfterReload(Path tempDir, boolean initialEnabled,
                                                 boolean flippedEnabled) throws Exception {
        writeIsolationConfig(tempDir, initialEnabled);

        UltiWorlds plugin = mock(UltiWorlds.class, CALLS_REAL_METHODS);
        plugin.setResourceFolderPath(tempDir.toString());
        doReturn(mock(PluginLogger.class)).when(plugin).getLogger();

        SimpleContainer container = mock(SimpleContainer.class);
        when(container.getBean(UltiToolsPlugin.class)).thenReturn(plugin);

        // Cold-start recording -- exactly what component scan does for real, via the sole real
        // gate (ComponentScanner.shouldRegister's only implementation).
        ConditionalRegistrationEvaluator.shouldRegister(InventoryIsolationService.class, container);

        // Operator edits the file, then issues `ul reload`.
        writeIsolationConfig(tempDir, flippedEnabled);

        try (MockedStatic<UltiTools> ultiToolsStatic = mockStatic(UltiTools.class);
             MockedStatic<ConditionalRegistrationEvaluator> evaluatorStatic =
                     mockStatic(ConditionalRegistrationEvaluator.class, CALLS_REAL_METHODS)) {

            UltiTools fakeCore = mock(UltiTools.class);
            when(fakeCore.getConfigManager()).thenReturn(mock(ConfigManager.class));
            when(fakeCore.getConfig()).thenReturn(mock(FileConfiguration.class));
            ultiToolsStatic.when(UltiTools::getInstance).thenReturn(fakeCore);

            plugin.reloadSelf();

            evaluatorStatic.verify(
                    () -> ConditionalRegistrationEvaluator.reportDrift(any()),
                    times(1));
        }
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
