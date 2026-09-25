package com.ultikits.plugins.worlds.config;

import com.ultikits.plugins.worlds.UltiWorlds;
import com.ultikits.plugins.worlds.i18n.CatalogueText;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The six {@code config/worlds.yml} keys nothing ever read are removed (maintainer ruling 2026-09-24
 * (d)); an operator whose file still holds one is told so, once per key, naming the module, the file
 * and the key. The removed-key check and the module's seam are reached by reflection so this file
 * compiles against a tree that does not have them yet, which a revert proof needs.
 */
@DisplayName("Removed config/worlds.yml keys are reported when still present")
class RemovedConfigKeysTest {

    private static final List<String> REMOVED = Arrays.asList("gui_title", "messages.world_teleport",
            "messages.world_not_found", "messages.no_permission", "messages.world_created",
            "messages.world_deleted");

    @TempDir
    Path tempDir;

    private File writeConfig(boolean withRemovedKeys) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("default_world", "world");
        if (withRemovedKeys) {
            for (String key : REMOVED) {
                yaml.set(key, "operator text");
            }
        }
        File file = new File(tempDir.toFile(), "worlds.yml");
        yaml.save(file);
        return file;
    }

    private static List<String> warningsFor(File file, UltiToolsPlugin plugin) throws Exception {
        Class<?> checker = Class.forName("com.ultikits.plugins.worlds.config.RemovedConfigKeys");
        Method warn = checker.getMethod("warnAboutLeftovers", File.class, Consumer.class, UltiToolsPlugin.class);
        List<String> warnings = new ArrayList<>();
        Consumer<String> sink = warnings::add;
        warn.invoke(null, file, sink, plugin);
        return warnings;
    }

    private static UltiToolsPlugin pluginIn(String code) {
        UltiToolsPlugin plugin = mock(UltiToolsPlugin.class);
        when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer(code));
        return plugin;
    }

    @Test
    @DisplayName("Positive control: each of the six keys still in the file gets its own warning, in the server's language")
    void warnsOncePerLeftoverKey() throws Exception {
        File file = writeConfig(true);
        for (String code : new String[] {"en", "zh"}) {
            List<String> expected = new ArrayList<>();
            for (String key : REMOVED) {
                String reason = "gui_title".equals(key)
                        ? CatalogueText.text(code, "removed_key_reason_gui_title")
                        : CatalogueText.text(code, "removed_key_reason_message");
                expected.add(CatalogueText.text(code, "removed_key_warning")
                        .replace("{FILE}", file.getPath()).replace("{KEY}", key).replace("{REASON}", reason));
            }

            assertThat(warningsFor(file, pluginIn(code))).as("language %s", code).containsExactlyElementsOf(expected);
        }
    }

    @Test
    @DisplayName("A file without the removed keys, or no file at all, produces no warning")
    void silentWithoutLeftovers() throws Exception {
        assertThat(warningsFor(writeConfig(false), pluginIn("en"))).isEmpty();
        assertThat(warningsFor(new File(tempDir.toFile(), "absent.yml"), pluginIn("en"))).isEmpty();
    }

    @Test
    @DisplayName("The module runs the check when it is enabled and when it is reloaded")
    void moduleRunsTheCheckOnEnableAndReload() throws Exception {
        File file = writeConfig(true);
        UltiWorlds plugin = mock(UltiWorlds.class);
        PluginLogger logger = mock(PluginLogger.class);
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        Method seam = UltiWorlds.class.getDeclaredMethod("operatorConfigFile");
        seam.setAccessible(true); // NOPMD - the seam is package-private in another package
        seam.invoke(doReturn(file).when(plugin));
        when(plugin.registerSelf()).thenCallRealMethod();
        Method onReload = UltiWorlds.class.getDeclaredMethod("onReload");
        onReload.setAccessible(true); // NOPMD - protected hook
        onReload.invoke(org.mockito.Mockito.doCallRealMethod().when(plugin));
        String first = CatalogueText.text("en", "removed_key_warning").replace("{FILE}", file.getPath())
                .replace("{KEY}", "gui_title").replace("{REASON}", CatalogueText.text("en", "removed_key_reason_gui_title"));

        plugin.registerSelf();
        onReload.invoke(plugin);

        verify(logger, org.mockito.Mockito.times(2)).warn(first);
    }
}
