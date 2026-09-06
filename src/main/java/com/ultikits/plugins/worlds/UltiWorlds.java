package com.ultikits.plugins.worlds;

import java.util.Arrays;
import java.util.List;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.UltiToolsModule;

/**
 * UltiWorlds - Multi-world management module.
 * Provides world creation, teleportation, per-world settings,
 * world protection, and inventory isolation.
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
    public void unregisterSelf() {
        getLogger().info(i18n("worlds_disabled"));
    }

    @Override
    public void reloadSelf() {
        // super.reloadSelf() reloads this module's own bound config entities (WorldConfig) and
        // reports @ConditionalOnConfig drift (ConditionalRegistrationEvaluator.reportDrift) --
        // the only signal an operator gets that a flag flipped without a restart. Omitting it
        // silently dropped both for every conditional class in this module, not just
        // InventoryIsolationService (UltiWorlds#10).
        super.reloadSelf();
        getLogger().info("UltiWorlds configuration reloaded!");
    }

    @Override
    public List<String> supported() {
        return Arrays.asList("zh", "en");
    }
}
