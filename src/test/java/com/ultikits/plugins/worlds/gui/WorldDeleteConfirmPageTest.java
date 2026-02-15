package com.ultikits.plugins.worlds.gui;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;
import com.ultikits.plugins.worlds.service.WorldService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.junit.jupiter.api.*;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for WorldDeleteConfirmPage.
 *
 * @author wisdomme
 * @version 2.0.0
 */
@DisplayName("WorldDeleteConfirmPage Tests")
class WorldDeleteConfirmPageTest {

    private WorldService mockWorldService;
    private UltiToolsPlugin mockPlugin;
    private Player mockPlayer;

    @BeforeEach
    void setUp() throws Exception {
        UltiWorldsTestHelper.setUp();
        mockPlugin = UltiWorldsTestHelper.getMockPlugin();
        mockWorldService = mock(WorldService.class);
        mockPlayer = UltiWorldsTestHelper.createMockPlayer("TestPlayer", UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiWorldsTestHelper.tearDown();
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("constructor should create confirm page")
        void constructorCreatesPage() {
            WorldDeleteConfirmPage page = new WorldDeleteConfirmPage(
                    mockPlayer, mockWorldService, "test_world", mockPlugin);

            assertThat(page).isNotNull();
        }
    }

    @Nested
    @DisplayName("onConfirm")
    class OnConfirmTests {

        @Test
        @DisplayName("onConfirm should delete world and send success message")
        void onConfirmSuccess() throws Exception {
            when(mockWorldService.deleteWorld("test_world")).thenReturn(true);

            WorldDeleteConfirmPage page = new WorldDeleteConfirmPage(
                    mockPlayer, mockWorldService, "test_world", mockPlugin);

            Method method = WorldDeleteConfirmPage.class.getDeclaredMethod("onConfirm", InventoryClickEvent.class);
            method.setAccessible(true); // NOPMD - test reflection

            InventoryClickEvent event = mock(InventoryClickEvent.class);
            method.invoke(page, event);

            verify(mockWorldService).deleteWorld("test_world");
            verify(mockPlayer).sendMessage(anyString());
        }

        @Test
        @DisplayName("onConfirm should send failure message when delete fails")
        void onConfirmFailure() throws Exception {
            when(mockWorldService.deleteWorld("test_world")).thenReturn(false);

            WorldDeleteConfirmPage page = new WorldDeleteConfirmPage(
                    mockPlayer, mockWorldService, "test_world", mockPlugin);

            Method method = WorldDeleteConfirmPage.class.getDeclaredMethod("onConfirm", InventoryClickEvent.class);
            method.setAccessible(true); // NOPMD - test reflection

            InventoryClickEvent event = mock(InventoryClickEvent.class);
            method.invoke(page, event);

            verify(mockWorldService).deleteWorld("test_world");
            verify(mockPlayer).sendMessage(anyString());
        }
    }

    @Nested
    @DisplayName("onCancel")
    class OnCancelTests {

        @Test
        @DisplayName("onCancel should send cancelled message")
        void onCancelSendsMessage() throws Exception {
            WorldDeleteConfirmPage page = new WorldDeleteConfirmPage(
                    mockPlayer, mockWorldService, "test_world", mockPlugin);

            Method method = WorldDeleteConfirmPage.class.getDeclaredMethod("onCancel", InventoryClickEvent.class);
            method.setAccessible(true); // NOPMD - test reflection

            InventoryClickEvent event = mock(InventoryClickEvent.class);
            method.invoke(page, event);

            verify(mockPlayer).sendMessage(anyString());
            verify(mockWorldService, never()).deleteWorld(anyString());
        }
    }

    @Nested
    @DisplayName("i18n Helper")
    class I18nHelper {

        @Test
        @DisplayName("i18n should delegate to plugin")
        void i18nDelegatesToPlugin() throws Exception {
            WorldDeleteConfirmPage page = new WorldDeleteConfirmPage(
                    mockPlayer, mockWorldService, "test_world", mockPlugin);

            Method method = WorldDeleteConfirmPage.class.getDeclaredMethod("i18n", String.class);
            method.setAccessible(true); // NOPMD - test reflection

            String result = (String) method.invoke(page, "test.key");

            assertThat(result).isEqualTo("test.key");
            verify(mockPlugin).i18n("test.key");
        }
    }
}
