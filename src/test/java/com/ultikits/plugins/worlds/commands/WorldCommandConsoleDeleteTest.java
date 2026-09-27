package com.ultikits.plugins.worlds.commands;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;
import com.ultikits.plugins.worlds.config.WorldConfig;
import com.ultikits.plugins.worlds.entity.WorldSettings;
import com.ultikits.plugins.worlds.gui.DeleteConfirmPageDriver;
import com.ultikits.plugins.worlds.gui.WorldDeleteConfirmPage;
import com.ultikits.plugins.worlds.service.DeleteConfirmationWindow;
import com.ultikits.plugins.worlds.service.WorldService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.Query;

import org.bukkit.Bukkit;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code UltiKits/UltiWorlds#19}, console half (maintainer's answer to the follow-up on question 14,
 * 2026-09-24): the server console may run {@code /world delete <name>}, but the first request
 * deletes nothing. It runs every check the player path runs, records a pending confirmation for
 * that sender and that exact world name, and asks for the same command again within 30 seconds.
 * Only a repeat inside that window deletes, and only after every check has run again.
 *
 * <p>A real {@link WorldService} and real world folders in a temporary world container sit behind
 * the command, so "deletes" and "deletes nothing" are read off the disk. The command's clock is
 * replaced by {@link #now}, so no test sleeps.
 *
 * @author wisdomme
 * @version 2.0.0
 */
@DisplayName("/world delete from the console needs a repeat within 30 seconds (UltiWorlds#19)")
class WorldCommandConsoleDeleteTest {

    private static final String WORLD = "scratchw";
    private static final String OTHER = "otherw";

    private WorldCommand command;
    private WorldService worldService;
    private WorldConfig mockConfig;
    private UltiToolsPlugin mockPlugin;
    private File container;
    private File worldFolder;
    private File otherFolder;
    private ConsoleCommandSender console;
    private final AtomicLong now = new AtomicLong(1_000_000L);
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        UltiWorldsTestHelper.setUp();
        mockPlugin = UltiWorldsTestHelper.getMockPlugin();

        mockConfig = UltiWorldsTestHelper.createDefaultConfig();
        when(mockConfig.getDefaultWorld()).thenReturn("world");
        when(mockConfig.getProtectedWorlds())
                .thenReturn(Arrays.asList("world", "world_nether", "world_the_end"));
        DataOperator<WorldSettings> dataOperator = mock(DataOperator.class);
        Query<WorldSettings> query = mock(Query.class);
        when(dataOperator.query()).thenReturn(query);
        when(query.where(anyString())).thenReturn(query);
        when(query.eq(any())).thenReturn(query);
        when(query.first()).thenReturn(null);
        when(query.delete()).thenReturn(0);

        worldService = new WorldService();
        UltiWorldsTestHelper.setField(worldService, "config", mockConfig);
        UltiWorldsTestHelper.setField(worldService, "dataOperator", dataOperator);
        UltiWorldsTestHelper.setField(worldService, "plugin", mockPlugin);

        command = new WorldCommand();
        UltiWorldsTestHelper.setField(command, "worldService", worldService);
        UltiWorldsTestHelper.setField(command, "plugin", mockPlugin);
        UltiWorldsTestHelper.setField(worldService, "deleteConfirmationWindow",
                new DeleteConfirmationWindow(now::get));

        container = Files.createTempDirectory("p17w2console").toFile();
        worldFolder = new File(container, WORLD);
        assertThat(new File(worldFolder, "region").mkdirs()).isTrue();
        otherFolder = new File(container, OTHER);
        assertThat(new File(otherFolder, "region").mkdirs()).isTrue();

        console = mock(ConsoleCommandSender.class);
        when(console.hasPermission(anyString())).thenReturn(true);
        when(console.getName()).thenReturn("CONSOLE");

        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.getWorld(anyString())).thenReturn(null);
        bukkit.when(Bukkit::getWorldContainer).thenReturn(container);
    }

    @AfterEach
    void tearDown() throws Exception {
        bukkit.close();
        deleteRecursively(container);
        UltiWorldsTestHelper.tearDown();
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    /**
     * {@code WorldCommand#deleteWorld(CommandSender, String)}, called reflectively so that this
     * class compiles against a command whose delete handler still takes a {@code Player} -- the
     * shape before this change -- and fails there at run time, test by test, instead of stopping
     * the whole test tree from compiling.
     */
    private void delete(CommandSender sender, String name) {
        try {
            WorldCommand.class.getMethod("deleteWorld", CommandSender.class, String.class)
                    .invoke(command, sender, name);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("WorldCommand#deleteWorld(CommandSender, String) is not callable", e);
        }
    }

    private List<String> sentTo(CommandSender sender) {
        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(sender, atLeast(0)).sendMessage(sent.capture());
        return sent.getAllValues();
    }

    @Test
    @DisplayName("the first request deletes nothing and asks for the same command within 30 seconds")
    void theFirstRequestDeletesNothing() {
        delete(console, WORLD);

        assertThat(worldFolder).exists();
        assertThat(new File(worldFolder, "region")).exists();
        verify(mockPlugin).i18n("world.delete.confirm_console");
        assertThat(sentTo(console)).containsExactly("world.delete.confirm_console");
    }

    @Test
    @DisplayName("repeating the same command inside the window deletes the world and reports it")
    void aRepeatInsideTheWindowDeletes() {
        delete(console, WORLD);
        now.addAndGet(29_000L);
        delete(console, WORLD);

        assertThat(worldFolder).doesNotExist();
        assertThat(sentTo(console)).containsExactly(
                "world.delete.confirm_console", "world.delete.deleting", "world.delete.success");
    }

    @Test
    @DisplayName("a repeat exactly 30 seconds later still confirms")
    void aRepeatAtTheBoundaryDeletes() {
        delete(console, WORLD);
        now.addAndGet(30_000L);
        delete(console, WORLD);

        assertThat(worldFolder).doesNotExist();
    }

    @Test
    @DisplayName("a repeat after the window deletes nothing and starts a new window, which then works")
    void aRepeatAfterTheWindowStartsAgain() {
        delete(console, WORLD);
        now.addAndGet(30_001L);
        delete(console, WORLD);

        assertThat(worldFolder).exists();
        assertThat(sentTo(console)).containsExactly(
                "world.delete.confirm_console", "world.delete.confirm_console");

        now.addAndGet(10_000L);
        delete(console, WORLD);
        assertThat(worldFolder).as("the expired repeat opened a new window").doesNotExist();
    }

    @Test
    @DisplayName("a repeat after this module deleted the world for another request deletes nothing and starts a new request")
    void aRepeatAfterAnInModuleDeletionDeletesNothing() throws Exception {
        delete(console, WORLD);

        // Another request deletes the world through this module; then a different world is
        // created under the same name.
        assertThat(worldService.deleteWorld(WORLD)).isTrue();
        assertThat(new File(worldFolder, "region").mkdirs()).isTrue();
        File marker = new File(worldFolder, "replacement.marker");
        assertThat(marker.createNewFile()).isTrue();
        now.addAndGet(5_000L);
        delete(console, WORLD);

        assertThat(marker).as("the world created afterwards survives the repeat").exists();
        assertThat(sentTo(console)).containsExactly(
                "world.delete.confirm_console", "world.delete.invalidated", "world.delete.confirm_console");

        // Control: the voided repeat was recorded as a new request for the world there now, and
        // repeating it inside the window confirms that one.
        now.addAndGet(5_000L);
        delete(console, WORLD);
        assertThat(worldFolder).doesNotExist();
    }

    @Test
    @DisplayName("a clock that steps backwards between request and repeat does not confirm")
    void aBackwardsClockDoesNotConfirm() {
        delete(console, WORLD);
        now.addAndGet(-1_000L);
        delete(console, WORLD);

        assertThat(worldFolder).exists();
        assertThat(sentTo(console)).containsExactly(
                "world.delete.confirm_console", "world.delete.confirm_console");
    }

    @Test
    @DisplayName("a different world name does not confirm the first one, and does not cancel it")
    void aDifferentNameDoesNotConfirm() {
        delete(console, WORLD);
        now.addAndGet(5_000L);
        delete(console, OTHER);

        assertThat(worldFolder).exists();
        assertThat(otherFolder).exists();

        now.addAndGet(5_000L);
        delete(console, WORLD);
        assertThat(worldFolder).doesNotExist();
        assertThat(otherFolder).exists();
    }

    @Test
    @DisplayName("the same name in other letter case is a different command and does not confirm")
    void aDifferentlyCasedNameDoesNotConfirm() {
        delete(console, WORLD);
        now.addAndGet(5_000L);
        delete(console, "SCRATCHW");

        assertThat(worldFolder).exists();
    }

    @Test
    @DisplayName("a world protected between request and repeat is refused, and the refusal clears the request")
    void aProtectedWorldIsRefusedEvenAfterConfirmation() {
        delete(console, WORLD);
        when(mockConfig.getProtectedWorlds()).thenReturn(Arrays.asList("world", WORLD));
        now.addAndGet(5_000L);
        delete(console, WORLD);

        assertThat(worldFolder).exists();
        assertThat(new File(worldFolder, "region")).exists();
        verify(mockPlugin).i18n("world.delete.protected");

        // Unprotected again, still inside the first window: the refused repeat consumed the
        // request, so this is a new first request, not a confirmation.
        when(mockConfig.getProtectedWorlds()).thenReturn(Arrays.asList("world"));
        now.addAndGet(5_000L);
        delete(console, WORLD);
        assertThat(worldFolder).exists();
    }

    @Test
    @DisplayName("the default world is refused on the request and on the repeat, and nothing is recorded")
    void theDefaultWorldIsRefused() {
        File defaultFolder = new File(container, "world");
        assertThat(new File(defaultFolder, "region").mkdirs()).isTrue();

        delete(console, "world");
        now.addAndGet(5_000L);
        delete(console, "world");

        assertThat(defaultFolder).exists();
        assertThat(sentTo(console)).containsExactly("world.delete.default", "world.delete.default");
    }

    @Test
    @DisplayName("a second repeat after a successful delete does nothing")
    void aSecondRepeatAfterSuccessDoesNothing() {
        delete(console, WORLD);
        now.addAndGet(1_000L);
        delete(console, WORLD);
        assertThat(worldFolder).doesNotExist();
        clearInvocations(console);

        now.addAndGet(1_000L);
        delete(console, WORLD);
        assertThat(sentTo(console)).containsExactly("world.not_found");
    }

    @Test
    @DisplayName("a pending request is consumed once: a folder recreated under the same name is not deleted by a third repeat")
    void aRequestIsConsumedOnce() {
        delete(console, WORLD);
        now.addAndGet(1_000L);
        delete(console, WORLD);
        assertThat(worldFolder).doesNotExist();

        assertThat(new File(worldFolder, "region").mkdirs()).isTrue();
        clearInvocations(console);
        now.addAndGet(1_000L);
        delete(console, WORLD);

        assertThat(worldFolder).as("recreated folder survives: no leftover confirmation").exists();
        // Consumed, not merely voided by the deletion it confirmed: the third call finds no
        // pending request at all, so it is a plain first request -- no "invalidated" line.
        assertThat(sentTo(console)).containsExactly("world.delete.confirm_console");
    }

    @Test
    @DisplayName("a missing world is refused and records nothing")
    void aMissingWorldIsRefused() {
        delete(console, "ghostw");
        now.addAndGet(1_000L);
        assertThat(new File(container, "ghostw").mkdirs()).isTrue();
        delete(console, "ghostw");

        assertThat(new File(container, "ghostw")).as("the refused request left nothing to confirm").exists();
        assertThat(sentTo(console)).containsExactly("world.not_found", "world.delete.confirm_console");
    }

    @Test
    @DisplayName("a sender that is neither a player nor the console (a command block) is refused and deletes nothing")
    void aCommandBlockIsRefused() {
        BlockCommandSender block = mock(BlockCommandSender.class);
        when(block.hasPermission(anyString())).thenReturn(true);
        when(block.getName()).thenReturn("@");

        delete(block, WORLD);
        now.addAndGet(1_000L);
        delete(block, WORLD);

        assertThat(worldFolder).exists();
        assertThat(sentTo(block)).containsExactly(
                "world.delete.sender_not_allowed", "world.delete.sender_not_allowed");
    }

    @Test
    @DisplayName("RCON is refused and deletes nothing, while the console with the same steps is asked to confirm")
    void rconIsRefused() {
        // Maintainer decision 2026-09-24 (question 14, third follow-up): RCON stays refused.
        // RemoteConsoleCommandSender is not a ConsoleCommandSender, so it never reaches the
        // confirmation table.
        org.bukkit.command.RemoteConsoleCommandSender rcon =
                mock(org.bukkit.command.RemoteConsoleCommandSender.class);
        when(rcon.hasPermission(anyString())).thenReturn(true);
        when(rcon.getName()).thenReturn("Rcon");

        delete(rcon, WORLD);
        now.addAndGet(1_000L);
        delete(rcon, WORLD);

        assertThat(worldFolder).exists();
        assertThat(sentTo(rcon)).containsExactly(
                "world.delete.sender_not_allowed", "world.delete.sender_not_allowed");

        // Control: the console, same world, same steps, is admitted and asked to confirm; and the
        // refused RCON requests left nothing for the console's first request to confirm.
        delete(console, WORLD);
        assertThat(worldFolder).exists();
        assertThat(sentTo(console)).containsExactly("world.delete.confirm_console");
    }

    @Test
    @DisplayName("control: a player still gets the window, never the console confirmation")
    void aPlayerStillGetsTheWindow() {
        org.bukkit.entity.Player player =
                UltiWorldsTestHelper.createMockPlayer("Admin", java.util.UUID.randomUUID());
        List<List<Object>> opened = new ArrayList<>();
        try (MockedConstruction<WorldDeleteConfirmPage> pages = DeleteConfirmPageDriver.intercept(opened)) {
            delete(player, WORLD);
            delete(player, WORLD);
        }
        assertThat(opened).hasSize(2);
        assertThat(worldFolder).exists();
        verify(mockPlugin, org.mockito.Mockito.never()).i18n("world.delete.confirm_console");
    }
}
