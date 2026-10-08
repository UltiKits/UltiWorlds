package com.ultikits.plugins.worlds;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The release metadata a 6.3.0 framework reads from this module.
 * <ul>
 *   <li>{@code api-version: 630}: the module's auto-unload check uses the framework's config-bound
 *       {@code @Scheduled}, which 6.3.0 refuses for a module declaring less, and which an older
 *       framework would silently ignore (the check would run once at load, never again).</li>
 *   <li>{@code identify-string}: the framework's update check and {@code /upm update} skip a module
 *       without one, and match a module by it; it must equal the catalogue's {@code identifyString},
 *       which {@code ultikits.json} carries.</li>
 *   <li>The README names UltiTools-API 6.3.0 as the minimum, the first thing a server owner reads.</li>
 * </ul>
 * Files outside the classpath are found from the project directory Surefire reports
 * ({@code basedir}), not from whatever directory the runner started in.
 */
@DisplayName("plugin.yml declares api-version 630 and the catalogue's identify-string; the README names 6.3.0")
class PluginMetadataTest {

    private static final Pattern IDENTIFY = Pattern.compile("\"identifyString\"\\s*:\\s*\"([^\"]*)\"");

    private static YamlConfiguration shippedPluginYml() throws IOException {
        InputStream in = PluginMetadataTest.class.getClassLoader().getResourceAsStream("plugin.yml");
        assertThat(in).as("shipped plugin.yml").isNotNull();
        try {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } finally {
            in.close();
        }
    }

    private static String projectFile(String name) throws IOException {
        Path path = Paths.get(name);
        String basedir = System.getProperty("basedir");
        if (basedir != null && Files.isRegularFile(Paths.get(basedir, name))) {
            path = Paths.get(basedir, name);
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("api-version is 630")
    void apiVersionIs630() throws Exception {
        YamlConfiguration yml = shippedPluginYml();
        assertThat(yml.getString("name")).as("control: the shipped plugin.yml was read").isEqualTo("UltiWorlds");
        assertThat(yml.getInt("api-version", -1)).isEqualTo(630);
    }

    @Test
    @DisplayName("identify-string equals ultikits.json's identifyString (ultiworlds)")
    void identifyStringMatchesCatalogue() throws Exception {
        Matcher matcher = IDENTIFY.matcher(projectFile("ultikits.json"));
        assertThat(matcher.find()).as("control: ultikits.json carries an identifyString").isTrue();
        String catalogue = matcher.group(1);
        assertThat(catalogue).isEqualTo("ultiworlds");
        assertThat(shippedPluginYml().getString("identify-string")).isEqualTo(catalogue);
    }

    @Test
    @DisplayName("README names UltiTools-API 6.3.0 as the minimum, in its badge and in both languages")
    void readmeNamesTheMinimum() throws Exception {
        String readme = projectFile("README.md");
        assertThat(readme).as("control: the README was read").contains("UltiWorlds");
        assertThat(readme).contains("UltiTools--API-6.3.0%2B");
        assertThat(readme).contains("UltiTools-API 6.3.0 or later");
        assertThat(readme).contains("UltiTools-API 6.3.0 或更高");
    }
}
