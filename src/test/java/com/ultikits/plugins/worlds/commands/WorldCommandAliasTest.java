package com.ultikits.plugins.worlds.commands;

import com.ultikits.ultitools.annotations.command.CmdExecutor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code w} is a vanilla command label ({@code /msg}'s alias, with {@code tell}), so {@code /world}
 * no longer registers it (UltiKits/UltiWorlds#45, maintainer decision 2026-09-27: a module alias
 * equal to a vanilla label is removed).
 */
@DisplayName("/world does not take the vanilla label w (UltiKits/UltiWorlds#45)")
class WorldCommandAliasTest {

    @Test
    @DisplayName("the aliases are exactly world and worlds")
    void aliases() {
        CmdExecutor executor = WorldCommand.class.getAnnotation(CmdExecutor.class);
        assertThat(executor).as("control: the annotation was read").isNotNull();
        assertThat(executor.alias()).containsExactly("world", "worlds");
    }
}
