package com.ultikits.plugins.worlds.util;

/**
 * Fills a language line's placeholders in one left-to-right pass.
 * <p>
 * Filling them one {@code String#replace} after another rescans every value already inserted, so
 * a path, a world name or text an operator typed that happens to contain a later placeholder was
 * rewritten (UltiKits/UltiWorlds#43). Here each value is inserted once, as written.
 */
public final class Placeholders {

    private Placeholders() {
    }

    /**
     * {@code template} with each placeholder replaced by its value, in one pass.
     *
     * @param template           the language line
     * @param placeholdersValues placeholder, value, placeholder, value, ...
     * @return the filled line
     */
    public static String fill(String template, String... placeholdersValues) {
        StringBuilder out = new StringBuilder(template.length());
        int i = 0;
        outer:
        while (i < template.length()) {
            for (int p = 0; p + 1 < placeholdersValues.length; p += 2) {
                String placeholder = placeholdersValues[p];
                if (!placeholder.isEmpty() && template.startsWith(placeholder, i)) {
                    out.append(placeholdersValues[p + 1]);
                    i += placeholder.length();
                    continue outer;
                }
            }
            out.append(template.charAt(i));
            i++;
        }
        return out.toString();
    }
}
