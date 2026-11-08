package com.ultikits.plugins.worlds.testutil;

import com.ultikits.plugins.worlds.MockBukkitSupport;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Initialises Bukkit's and Paper's tag constants once per test JVM, against a live MockBukkit
 * server, before the first test class runs (UltiKits/UltiWorlds#77).
 * <p>
 * Those constants are resolved through {@code Bukkit.getTag(...)} when their class is first
 * initialised. If that first initialisation happens inside a default-answer
 * {@code mockStatic(Bukkit.class)}, every constant becomes {@code null} for the rest of the JVM and
 * later item and GUI tests fail depending on class order (UltiKits/UltiWorlds#74). Registered for
 * every test through {@code junit-platform.properties}, like {@link UnimplementedIsFailure}.
 */
public class RegistryTagsWarmup implements BeforeAllCallback {

    private static final AtomicBoolean DONE = new AtomicBoolean();

    private static final String[] TAG_HOLDERS = {
        "org.bukkit.Tag",
        "com.destroystokyo.paper.MaterialTags",
    };

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (!DONE.compareAndSet(false, true)) {
            return;
        }
        boolean ownServer = Bukkit.getServer() == null;
        if (ownServer) {
            MockBukkitSupport.ensureCleanState();
            MockBukkit.mock();
        }
        try {
            ClassLoader loader = RegistryTagsWarmup.class.getClassLoader();
            for (String holder : TAG_HOLDERS) {
                Class.forName(holder, true, loader);
            }
        } finally {
            if (ownServer) {
                MockBukkitSupport.safeUnmock();
            }
        }
    }
}
