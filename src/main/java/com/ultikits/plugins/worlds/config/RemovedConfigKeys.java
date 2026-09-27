package com.ultikits.plugins.worlds.config;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Reports configuration keys this module no longer reads but which are still sitting in the
 * operator's own {@code config/worlds.yml}.
 * <p>
 * Deleting a key from {@link WorldConfig} stops the framework writing it into a fresh file, but it
 * does nothing to the files already on disk: the framework only ever writes a declared default for a
 * key that is <em>missing</em>, so an existing install keeps the key, keeps whatever value the
 * operator gave it, and gets no indication that the value means nothing. This class is that
 * indication -- one warning per leftover key, naming the module, the file and the key, and saying
 * where the setting's text comes from.
 *
 * @author wisdomme
 * @version 2.0.0
 */
public final class RemovedConfigKeys {

    /**
     * Every key removed from {@code config/worlds.yml}, mapped to the language-file key of what an
     * operator should be told about it. Insertion order is the order the warnings are emitted in.
     * The values are informational: the text is read by {@link #reasonFor}, whose literal lookups the
     * language guard checks, so a key added here needs a case there too (a missing case fails loudly).
     */
    private static final Map<String, String> REMOVED;

    static {
        Map<String, String> removed = new LinkedHashMap<String, String>();
        removed.put("gui_title", "removed_key_reason_gui_title");
        removed.put("messages.world_teleport", "removed_key_reason_message");
        removed.put("messages.world_not_found", "removed_key_reason_message");
        removed.put("messages.no_permission", "removed_key_reason_message");
        removed.put("messages.world_created", "removed_key_reason_message");
        removed.put("messages.world_deleted", "removed_key_reason_message");
        REMOVED = Collections.unmodifiableMap(removed);
    }

    private RemovedConfigKeys() {
        // Utility class
    }

    /**
     * The keys this class knows about, in the order it reports them.
     *
     * @return an unmodifiable map of removed key path to the language-file key of the guidance printed
     *         for it
     */
    public static Map<String, String> removedKeys() {
        return REMOVED;
    }

    /**
     * The guidance printed for one removed key, from the language file. Each removed key names its own
     * catalogue text here, so a key added to {@link #REMOVED} without a case fails loudly instead of
     * being given another key's explanation.
     */
    private static String reasonFor(String removedKey, UltiToolsPlugin plugin) {
        switch (removedKey) {
            case "gui_title":
                return plugin.i18n("removed_key_reason_gui_title");
            case "messages.world_teleport":
            case "messages.world_not_found":
            case "messages.no_permission":
            case "messages.world_created":
            case "messages.world_deleted":
                return plugin.i18n("removed_key_reason_message");
            default:
                throw new IllegalStateException("No guidance for removed key " + removedKey);
        }
    }

    /**
     * Emit one warning per removed key that is still present in the operator's configuration file.
     * <p>
     * Silent when the file is absent, is not a regular file, or cannot be parsed -- there is then
     * nothing to report and nothing to be sure of. A parse failure is deliberately not reported
     * here: the framework's own config loading already fails loudly on an unparseable file, and a
     * second message from this check would only add noise to it.
     *
     * @param configFile the operator's {@code config/worlds.yml}; may be {@code null}
     * @param warn       where to send each warning, normally the module logger's warn method
     * @param plugin     the module, whose language file gives the warning its text
     */
    public static void warnAboutLeftovers(File configFile, Consumer<String> warn, UltiToolsPlugin plugin) {
        if (configFile == null || !configFile.isFile()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            return;
        }
        for (Map.Entry<String, String> entry : REMOVED.entrySet()) {
            if (yaml.contains(entry.getKey())) {
                // No "[UltiWorlds]" prefix: the module logger adds that itself, and the module is
                // still named in the sentence for any consumer that does not.
                String reason = reasonFor(entry.getKey(), plugin);
                warn.accept(plugin.i18n("removed_key_warning")
                        .replace("{FILE}", configFile.getPath())
                        .replace("{KEY}", entry.getKey())
                        .replace("{REASON}", reason));
            }
        }
    }
}
