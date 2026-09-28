package com.ultikits.plugins.worlds.service;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;
import com.ultikits.plugins.worlds.config.WorldConfig;
import com.ultikits.ultitools.annotations.ConfigEntry;
import com.ultikits.ultitools.annotations.Scheduled;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code auto_unload.check_interval} decides how often empty worlds are checked, in seconds; the
 * two deprecated keys that nothing read are gone (UltiKits/UltiWorlds#38). The check ran on a fixed
 * 60-second schedule whatever the key said.
 */
@DisplayName("The auto-unload check follows auto_unload.check_interval (UltiKits/UltiWorlds#38)")
class AutoUnloadIntervalTest {

    private WorldService service;
    private WorldConfig config;
    private Method tick;

    @BeforeEach
    void setUp() throws Exception {
        UltiWorldsTestHelper.setUp();
        config = UltiWorldsTestHelper.createDefaultConfig();
        WorldService real = new WorldService();
        UltiWorldsTestHelper.setField(real, "config", config);
        UltiWorldsTestHelper.setField(real, "plugin", UltiWorldsTestHelper.getMockPlugin());
        service = spy(real);
        doNothing().when(service).checkAutoUnloadEmptyWorlds();
    }

    /** Reached reflectively: without the fix the one-second tick does not exist. */
    private Method tick() throws NoSuchMethodException {
        if (tick == null) {
            tick = WorldService.class.getMethod("autoUnloadTick");
        }
        return tick;
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiWorldsTestHelper.tearDown();
    }

    private void seconds(int count) throws Exception {
        for (int i = 0; i < count; i++) {
            tick().invoke(service);
        }
    }

    @Test
    @DisplayName("interval 30: a check after the 30th second and the 60th, none before")
    void everyThirtySeconds() throws Exception {
        when(config.getEmptyWorldCheckInterval()).thenReturn(30);
        seconds(29);
        verify(service, never()).checkAutoUnloadEmptyWorlds();
        seconds(1);
        verify(service, times(1)).checkAutoUnloadEmptyWorlds();
        seconds(30);
        verify(service, times(2)).checkAutoUnloadEmptyWorlds();
    }

    @Test
    @DisplayName("interval 600: nothing after 60 seconds, the old fixed cadence")
    void tenMinutesIsNotOneMinute() throws Exception {
        when(config.getEmptyWorldCheckInterval()).thenReturn(600);
        seconds(599);
        verify(service, never()).checkAutoUnloadEmptyWorlds();
        seconds(1);
        verify(service, times(1)).checkAutoUnloadEmptyWorlds();
    }

    @Test
    @DisplayName("a value lowered by a reload applies at the next second")
    void loweredValueApplies() throws Exception {
        when(config.getEmptyWorldCheckInterval()).thenReturn(60);
        seconds(20);
        when(config.getEmptyWorldCheckInterval()).thenReturn(10);
        seconds(1);
        verify(service, times(1)).checkAutoUnloadEmptyWorlds();
    }

    @Test
    @DisplayName("the tick runs every second on the main thread, starting a second after start; the fixed schedule is gone")
    void schedule() throws Exception {
        Scheduled onTick = tick().getAnnotation(Scheduled.class);
        assertThat(onTick).isNotNull();
        assertThat(onTick.period()).isEqualTo(20L);
        assertThat(onTick.delay()).isEqualTo(20L);
        assertThat(onTick.async()).isFalse();
        assertThat(WorldService.class.getMethod("checkAutoUnloadEmptyWorlds").getAnnotation(Scheduled.class)).isNull();
    }

    @Test
    @DisplayName("unload_empty_worlds and unload_delay are declared nowhere: not in WorldConfig, not in the shipped file")
    void deprecatedKeysAreGone() throws Exception {
        List<String> paths = new ArrayList<>();
        for (Field field : WorldConfig.class.getDeclaredFields()) {
            ConfigEntry entry = field.getAnnotation(ConfigEntry.class);
            if (entry != null) {
                paths.add(entry.path());
            }
        }
        assertThat(paths).as("control: the entries were read").contains("auto_unload.check_interval");
        assertThat(paths).doesNotContain("unload_empty_worlds", "unload_delay");

        YamlConfiguration shipped = YamlConfiguration.loadConfiguration(new InputStreamReader(
                AutoUnloadIntervalTest.class.getClassLoader().getResourceAsStream("config/worlds.yml"),
                StandardCharsets.UTF_8));
        assertThat(shipped.contains("auto_unload.check_interval")).as("control: the shipped file was read").isTrue();
        assertThat(shipped.contains("unload_empty_worlds")).isFalse();
        assertThat(shipped.contains("unload_delay")).isFalse();
    }
}
