package com.ultikits.plugins.worlds.commands;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code /world help} lists every admin subcommand an admin can run, including {@code unprotect} and
 * {@code unblock}, whose help lines the language file carried but no code printed.
 */
@DisplayName("/world help lists unprotect and unblock for an admin")
class WorldCommandHelpTest {

    private WorldCommand command;

    @BeforeEach
    void setUp() throws Exception {
        UltiWorldsTestHelper.setUp();
        command = new WorldCommand();
        UltiWorldsTestHelper.setField(command, "plugin", UltiWorldsTestHelper.getMockPlugin());
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiWorldsTestHelper.tearDown();
    }

    @Test
    @DisplayName("an admin sees the unprotect and unblock lines, after block and before difficulty")
    void adminHelpListsUnprotectAndUnblock() {
        Player player = UltiWorldsTestHelper.createMockPlayer("Admin", UUID.randomUUID());
        when(player.hasPermission("ultiworlds.admin")).thenReturn(true);

        command.help(player);

        ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
        verify(player, atLeastOnce()).sendMessage(lines.capture());
        // The helper's i18n answers with the key itself, so each line names the key it printed.
        assertThat(lines.getAllValues()).containsSubsequence("help.block", "command.help.unprotect",
                "command.help.unblock", "command.help.difficulty");
    }
}
