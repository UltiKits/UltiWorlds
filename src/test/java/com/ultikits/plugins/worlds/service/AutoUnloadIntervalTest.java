package com.ultikits.plugins.worlds.service;

import com.ultikits.plugins.worlds.config.WorldConfig;
import com.ultikits.ultitools.annotations.ConfigEntry;
import com.ultikits.ultitools.annotations.Scheduled;
import com.ultikits.ultitools.annotations.config.Range;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/**
 * {@code auto_unload.check_interval} decides how often empty worlds are checked, in seconds, through the
 * framework's config-bound {@code @Scheduled} on {@link WorldService#checkAutoUnloadEmptyWorlds()} itself.
 * The module's own one-second counting tick, which existed only while the module declared
 * {@code api-version: 621}, is gone (follow-up to UltiKits/UltiWorlds#38).
 * <p>
 * Cadence and reload behaviour of a bound task (seconds times 20, a changed value applied at
 * {@code /ul reload} keeping the task's place in its cycle, an invalid value refusing the module at load
 * and kept out at reload) are the framework's and are tested there; this class pins that the module
 * hands its check to that mechanism, on the right key, and declares nothing that would stop it working.
 */
@DisplayName("The auto-unload check is bound to auto_unload.check_interval by the framework (UltiKits/UltiWorlds#38 follow-up)")
class AutoUnloadIntervalTest {

    private static final String KEY = "auto_unload.check_interval";

    private static Field intervalField() throws NoSuchFieldException {
        return WorldConfig.class.getDeclaredField("emptyWorldCheckInterval");
    }

    @Test
    @DisplayName("checkAutoUnloadEmptyWorlds carries @Scheduled bound to auto_unload.check_interval for period and delay, sync, no literal timing")
    void checkIsBoundToTheKey() throws Exception {
        Method check = WorldService.class.getMethod("checkAutoUnloadEmptyWorlds");
        Scheduled scheduled = check.getAnnotation(Scheduled.class);
        assertThat(scheduled).as("@Scheduled on checkAutoUnloadEmptyWorlds").isNotNull();
        assertThat(scheduled.config()).isEqualTo(WorldConfig.class);
        assertThat(scheduled.periodKey()).isEqualTo(KEY);
        assertThat(scheduled.delayKey()).isEqualTo(KEY);
        assertThat(scheduled.async()).as("a bound task must be sync; async refuses the module").isFalse();
        // The literals keep their "unset" defaults: a literal beside its key refuses the module at load.
        assertThat(scheduled.period()).isEqualTo(-1L);
        assertThat(scheduled.delay()).isEqualTo(0L);
    }

    @Test
    @DisplayName("the one-second counting tick and its counter are gone: no autoUnloadTick, no secondsSinceAutoUnloadCheck, one @Scheduled")
    void countingTickIsGone() {
        List<String> methods = new ArrayList<>();
        List<String> scheduledMethods = new ArrayList<>();
        for (Method method : WorldService.class.getDeclaredMethods()) {
            methods.add(method.getName());
            if (method.getAnnotation(Scheduled.class) != null) {
                scheduledMethods.add(method.getName());
            }
        }
        assertThat(methods).as("control: the methods were read").contains("checkAutoUnloadEmptyWorlds");
        assertThat(methods).doesNotContain("autoUnloadTick");
        assertThat(scheduledMethods).containsExactly("checkAutoUnloadEmptyWorlds");

        List<String> fields = new ArrayList<>();
        for (Field field : WorldService.class.getDeclaredFields()) {
            fields.add(field.getName());
        }
        assertThat(fields).as("control: the fields were read").contains("emptyWorldTimers");
        assertThat(fields).doesNotContain("secondsSinceAutoUnloadCheck");
    }

    @Test
    @DisplayName("the bound field carries no @Range, still defaults to 60, and registers the old comment text")
    void boundFieldHasNoRange() throws Exception {
        Field field = intervalField();
        ConfigEntry entry = field.getAnnotation(ConfigEntry.class);
        assertThat(entry).as("control: the bound field is the config entry").isNotNull();
        assertThat(entry.path()).isEqualTo(KEY);
        // A module @Range on a bound field makes an out-of-range reload throw from the config reload,
        // aborting the rest of the module's reload instead of keeping the running value.
        assertThat(field.getAnnotation(Range.class)).as("@Range on the bound field").isNull();
        assertThat(entry.comment()).contains("at least 10 recommended");
        assertThat(entry.previousComments()).contains("Check interval in seconds");

        WorldConfig config = mock(WorldConfig.class,
                withSettings().useConstructor("config/worlds.yml").defaultAnswer(CALLS_REAL_METHODS));
        assertThat(config.getEmptyWorldCheckInterval()).isEqualTo(60);
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
        assertThat(paths).as("control: the entries were read").contains(KEY);
        assertThat(paths).doesNotContain("unload_empty_worlds", "unload_delay");

        YamlConfiguration shipped = YamlConfiguration.loadConfiguration(new InputStreamReader(
                AutoUnloadIntervalTest.class.getClassLoader().getResourceAsStream("config/worlds.yml"),
                StandardCharsets.UTF_8));
        assertThat(shipped.contains(KEY)).as("control: the shipped file was read").isTrue();
        assertThat(shipped.contains("unload_empty_worlds")).isFalse();
        assertThat(shipped.contains("unload_delay")).isFalse();
    }
}
