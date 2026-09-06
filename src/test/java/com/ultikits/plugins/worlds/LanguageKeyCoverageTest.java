package com.ultikits.plugins.worlds;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guard test for real-machine finding F-W2 (Phase 13 UltiWorlds PR #16): every {@code
 * i18n("literal.key")} call site under {@code src/main/java} must resolve to an actual message in
 * both language files. Before this fix, {@code src/main/resources/lang/en.yml} and {@code zh.yml}
 * had no {@code world:} section and no {@code help:} section at all, so roughly fifty keys the
 * command class calls resolved to nothing and reached the player as raw dotted keys.
 *
 * <p>Every call site in this module passes a string literal directly to {@code i18n(...)} (checked
 * by reading every caller); none builds a key by concatenation, so a plain regex scan over the
 * source tree is exhaustive and does not need to model string-building.
 *
 * @author wisdomme
 * @version 2.0.0
 */
@DisplayName("Language key coverage (F-W2)")
class LanguageKeyCoverageTest {

    private static final Path SOURCE_ROOT = Paths.get("src", "main", "java");
    private static final Pattern I18N_CALL = Pattern.compile("i18n\\(\"([^\"]+)\"\\)");

    @Test
    @DisplayName("Every i18n key referenced from src/main/java resolves in lang/en.yml")
    void everyReferencedKeyResolvesInEnglish() throws IOException {
        assertAllReferencedKeysResolveIn("lang/en.yml");
    }

    @Test
    @DisplayName("Every i18n key referenced from src/main/java resolves in lang/zh.yml")
    void everyReferencedKeyResolvesInChinese() throws IOException {
        assertAllReferencedKeysResolveIn("lang/zh.yml");
    }

    private void assertAllReferencedKeysResolveIn(String resourceName) throws IOException {
        Set<String> referencedKeys = collectReferencedKeys();
        assertThat(referencedKeys)
                .as("i18n(\"...\") call sites found under " + SOURCE_ROOT)
                .isNotEmpty();

        Map<String, Object> resolvedKeys = loadFlattenedLangFile(resourceName);

        Set<String> missing = new TreeSet<>();
        for (String key : referencedKeys) {
            if (!resolvedKeys.containsKey(key)) {
                missing.add(key);
            }
        }

        assertThat(missing)
                .as("keys called via i18n(...) in src/main/java but absent from " + resourceName)
                .isEmpty();
    }

    /**
     * Enumerates every {@code i18n("literal.key")} call in {@code src/main/java}, across every
     * class that carries its own private {@code i18n(String)} wrapper delegating to the plugin
     * (WorldCommand, WorldCreateConversation, WorldListPage, WorldDeleteConfirmPage) as well as
     * WorldService, WorldListener and UltiWorlds, which call the framework's {@code i18n(String)}
     * directly.
     */
    private Set<String> collectReferencedKeys() throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        try (Stream<Path> paths = Files.walk(SOURCE_ROOT)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                            Matcher matcher = I18N_CALL.matcher(content);
                            while (matcher.find()) {
                                keys.add(matcher.group(1));
                            }
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
        return keys;
    }

    private Map<String, Object> loadFlattenedLangFile(String resourceName) throws IOException {
        Map<String, Object> flattened = new LinkedHashMap<>();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            assertThat(in).as("resource " + resourceName + " on the test classpath").isNotNull();
            Yaml yaml = new Yaml();
            Object raw = yaml.load(in);
            flatten("", raw, flattened);
        }
        return flattened;
    }

    /**
     * Flattens nested YAML mappings into dot-separated keys. Deliberately reads keys via {@code
     * Map<?, ?>} rather than {@code Map<String, Object>}: SnakeYAML's default (YAML 1.1) resolver
     * parses a handful of bare mapping keys already present in both lang files ({@code common.on} /
     * {@code common.off}) as {@link Boolean}, not {@link String}. Neither key is referenced by any
     * {@code i18n(...)} call site in this module, so this is a pre-existing quirk of the fixture,
     * not something this fix touches — {@code String.valueOf(...)} on the key just needs to not
     * throw for it, not reproduce SnakeYAML's own string form of "on"/"off".
     */
    private void flatten(String prefix, Object node, Map<String, Object> out) {
        if (!(node instanceof Map)) {
            out.put(prefix, node);
            return;
        }
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) node).entrySet()) {
            String keySegment = String.valueOf(entry.getKey());
            String key = prefix.isEmpty() ? keySegment : prefix + "." + keySegment;
            flatten(key, entry.getValue(), out);
        }
    }
}
