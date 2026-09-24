package com.ultikits.plugins.worlds.gui;

import com.ultikits.plugins.worlds.service.WorldService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import mc.obliviate.inventory.event.customclosevent.FakeInventoryCloseEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.PlayerInventory;
import org.mockito.MockedConstruction;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

/**
 * Drives a {@link WorldDeleteConfirmPage} the way a player does, for {@code UltiKits/UltiWorlds#19}.
 *
 * <p>A page cannot really be opened in a unit test: obliviate-invs' {@code Gui#open()} requires an
 * initialised {@code InventoryAPI}, which only a running server has. So a test that runs
 * {@code /world delete} intercepts the page's construction ({@link #intercept}), and records the
 * arguments the command passed. {@link #rebuild} then builds a real page from exactly those
 * arguments, so the page a test confirms is the page the command asked for -- the world name
 * included -- and not one the test assembled by hand.
 *
 * <p>Confirm and cancel call the page's own {@code onConfirm}/{@code onCancel}, which the page's OK
 * and Cancel buttons call (see {@code BaseConfirmationPage#createOkButton}). Close delivers a close
 * event to the page's real {@code onClose} (see {@link #close}).
 */
public final class DeleteConfirmPageDriver {

    private DeleteConfirmPageDriver() {
        // test utility
    }

    /**
     * Intercept every {@link WorldDeleteConfirmPage} constructed while the returned handle is
     * open. Each construction's arguments are appended to {@code sink}.
     */
    public static MockedConstruction<WorldDeleteConfirmPage> intercept(List<List<Object>> sink) {
        return mockConstruction(WorldDeleteConfirmPage.class,
                (page, context) -> sink.add(new ArrayList<>(context.arguments())));
    }

    /** Build a real page from arguments a command passed to the intercepted constructor. */
    public static WorldDeleteConfirmPage rebuild(List<Object> arguments) {
        return new WorldDeleteConfirmPage((Player) arguments.get(0), (WorldService) arguments.get(1),
                (String) arguments.get(2), (UltiToolsPlugin) arguments.get(3));
    }

    /**
     * What the OK button does: {@code onConfirm}, for a click that landed in the page's own (top)
     * inventory, at the OK slot. A test that holds {@code Bukkit} static-mocked builds pages whose
     * {@code Bukkit.createInventory} returned {@code null}; such a page is given a stand-in
     * inventory first, which is what a running server always gives it.
     */
    public static void confirm(WorldDeleteConfirmPage page) {
        Inventory top = page.getInventory();
        if (top == null) {
            top = mock(Inventory.class);
            page.setInventory(top);
        }
        page.onConfirm(clickIn(top, OK_SLOT));
    }

    /**
     * A click on the OK slot's index that lands in the player's own inventory instead of the page:
     * what obliviate-invs would deliver to a page it still believes is open (UltiKits/UltiWorlds#19,
     * gate-1 WR-01).
     */
    public static void clickOkSlotInPlayersOwnInventory(WorldDeleteConfirmPage page) {
        page.onConfirm(clickIn(mock(PlayerInventory.class), OK_SLOT));
    }

    /** The OK button's raw slot: bottom row, column 5, of a 3-row page. */
    public static final int OK_SLOT = 23;

    private static InventoryClickEvent clickIn(Inventory clicked, int slot) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getClickedInventory()).thenReturn(clicked);
        when(event.getRawSlot()).thenReturn(slot);
        when(event.getSlot()).thenReturn(slot);
        return event;
    }

    /** What the Cancel button does: {@code onCancel}. */
    public static void cancel(WorldDeleteConfirmPage page) {
        page.onCancel(mock(InventoryClickEvent.class));
    }

    /**
     * Close the page without pressing either button (Escape, or walking away). The event reaches
     * the page's real {@code onClose} -- {@code BaseInventoryPage}'s, which runs any close handler
     * the page registered and then obliviate-invs' own.
     *
     * <p>The event is obliviate's {@link FakeInventoryCloseEvent}, whose handling in
     * {@code Gui#onClose} returns before its only step, {@code stopAllTasks()}. A plain
     * {@link org.bukkit.event.inventory.InventoryCloseEvent} cannot be used here: obliviate-invs
     * 4.3.0 was compiled when {@code InventoryView} was a class, and on the Paper 1.21 API used by
     * these tests it is an interface, so that path fails here with
     * {@code IncompatibleClassChangeError}. How a running server links that call is not something a
     * unit test can observe; the real-machine checklist row closes the page by hand. Nothing this
     * module or {@code BaseInventoryPage} does on close is skipped.
     */
    public static void close(WorldDeleteConfirmPage page) {
        page.onClose(mock(FakeInventoryCloseEvent.class));
    }
}
