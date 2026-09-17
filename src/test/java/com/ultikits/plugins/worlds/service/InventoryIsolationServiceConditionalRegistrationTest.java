package com.ultikits.plugins.worlds.service;

import com.ultikits.plugins.worlds.UltiWorlds;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.context.ConditionalRegistrationEvaluator;
import com.ultikits.ultitools.context.SimpleContainer;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
 * <b>Reload:</b> the framework's documented evaluate-once-at-scan semantics mean a reload never
 * re-registers or unregisters anything; it only reports drift via
 * {@code ConditionalRegistrationEvaluator.reportDrift}. On UltiTools 6.3.0 that report is a step of
 * {@code UltiToolsPlugin.reloadSelf()}, which is {@code final} and whose steps are covered by the
 * framework's own suite. The defect UltiWorlds#10 recorded -- a module override of
 * {@code reloadSelf()} that skipped this report -- can therefore only come back if this module
 * declares its own {@code reloadSelf()}, which no longer compiles.
 * {@link #reloadIsTheFrameworkFinalMethodThatReportsDrift()} pins that structural fact instead of
 * driving the framework method through static mocks of framework managers (UltiWorlds#27).
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
    @DisplayName("reload is UltiToolsPlugin's final reloadSelf(), whose own steps report drift -- UltiWorlds declares no override that could skip it")
    void reloadIsTheFrameworkFinalMethodThatReportsDrift() throws Exception {
        Method reload = UltiWorlds.class.getMethod("reloadSelf");

        assertThat(reload.getDeclaringClass())
                .as("UltiWorlds must inherit reloadSelf() rather than declare its own")
                .isEqualTo(UltiToolsPlugin.class);
        assertThat(Modifier.isFinal(reload.getModifiers()))
                .as("the framework's reloadSelf() must be final, so no module can skip its drift report")
                .isTrue();
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
