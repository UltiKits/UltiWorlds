package com.ultikits.plugins.worlds;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import com.ultikits.plugins.worlds.config.RemovedConfigKeys;
import com.ultikits.plugins.worlds.config.WorldConfig;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.ConfigEntity;
import com.ultikits.ultitools.annotations.UltiToolsModule;

/**
 * UltiWorlds - Multi-world management module.
 * Provides world creation, teleportation, per-world settings,
 * world protection, and inventory isolation.
 * <p>
 * This class declares no unload override: UltiTools' final {@code reloadSelf()} re-reads
 * {@code config/worlds.yml} into {@code WorldConfig} and reports {@code @ConditionalOnConfig} drift
 * (such as a changed {@code world_isolation.enabled}), which an override that skipped the framework
 * call used to silently drop (UltiWorlds#10). Its {@link #onReload()} hook runs after that, and only
 * repeats the removed-key warning.
 *
 * @author wisdomme
 * @version 2.0.0
 */
@UltiToolsModule(
    scanBasePackages = {"com.ultikits.plugins.worlds"}
)
public class UltiWorlds extends UltiToolsPlugin {

    /**
     * {@link WorldConfig}'s file, relative to this module's folder -- its own {@link ConfigEntity}
     * value. Read once from the annotation so the removed-key check can never hold a second copy of
     * the path.
     */
    private static final String CONFIG_PATH = WorldConfig.class.getAnnotation(ConfigEntity.class).value();

    @Override
    public boolean registerSelf() {
        getLogger().info(i18n("worlds_enabled"));
        // Deleting a key from WorldConfig does nothing to the operator's existing file, so tell them
        // about any key this version no longer reads (maintainer ruling 2026-09-24 (d)).
        warnAboutRemovedConfigKeys();
        return true;
    }

    /**
     * Runs after the framework has re-read {@code config/worlds.yml}, on every reload of this module.
     * Repeats the removed-key warning, so an operator who edits a key this version no longer reads and
     * reloads is told it has no effect.
     */
    @Override
    protected void onReload() {
        warnAboutRemovedConfigKeys();
    }

    private void warnAboutRemovedConfigKeys() {
        // Advisory only: nothing it throws may cost the module its enable or its reload.
        try {
            RemovedConfigKeys.warnAboutLeftovers(operatorConfigFile(), getLogger()::warn, this);
        } catch (RuntimeException e) {
            getLogger().warn(e, i18n("log.removed_key_check_failed").replace("{FILE}", CONFIG_PATH));
        }
    }

    /**
     * The operator's own copy of this module's configuration file.
     * <p>
     * A seam, package-private on purpose. {@code UltiToolsPlugin#getConfigFile} is {@code protected}
     * and {@code final}, so a test in this package can neither call it nor stub it, and a mocked
     * plugin returns {@code null} from it.
     *
     * @return the file {@code config/worlds.yml} resolves to for this installation
     */
    File operatorConfigFile() {
        return getConfigFile(CONFIG_PATH);
    }

    @Override
    public List<String> supported() {
        return Arrays.asList("zh", "en");
    }
}
