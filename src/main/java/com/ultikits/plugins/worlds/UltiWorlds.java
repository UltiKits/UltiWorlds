package com.ultikits.plugins.worlds;

import java.util.Arrays;
import java.util.List;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.UltiToolsModule;

/**
 * UltiWorlds - Multi-world management module.
 * Provides world creation, teleportation, per-world settings,
 * world protection, and inventory isolation.
 * <p>
 * This class declares no reload or unload override: UltiTools' final {@code reloadSelf()} re-reads
 * {@code config/worlds.yml} into {@code WorldConfig} and reports {@code @ConditionalOnConfig} drift
 * (such as a changed {@code world_isolation.enabled}), which an override that skipped the framework
 * call used to silently drop (UltiWorlds#10).
 *
 * @author wisdomme
 * @version 2.0.0
 */
@UltiToolsModule(
    scanBasePackages = {"com.ultikits.plugins.worlds"}
)
public class UltiWorlds extends UltiToolsPlugin {

    @Override
    public boolean registerSelf() {
        getLogger().info(i18n("worlds_enabled"));
        return true;
    }

    @Override
    public List<String> supported() {
        return Arrays.asList("zh", "en");
    }
}
