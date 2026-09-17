package com.ultikits.plugins.worlds;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import org.junit.jupiter.api.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for UltiWorlds main plugin class.
 *
 * @author wisdomme
 * @version 2.0.0
 */
@DisplayName("UltiWorlds Main Class Tests")
class UltiWorldsTest {

    @Nested
    @DisplayName("Plugin Lifecycle")
    class PluginLifecycle {

        @Test
        @DisplayName("registerSelf should return true")
        void registerSelf() throws Exception {
            UltiWorlds plugin = mock(UltiWorlds.class);
            PluginLogger logger = mock(PluginLogger.class);
            when(plugin.getLogger()).thenReturn(logger);
            when(plugin.i18n(anyString())).thenReturn("worlds_enabled");
            when(plugin.registerSelf()).thenCallRealMethod();

            boolean result = plugin.registerSelf();

            assertThat(result).isTrue();
            verify(logger).info("worlds_enabled");
        }
    }

    @Nested
    @DisplayName("Lifecycle contract (UltiKits/UltiWorlds#27)")
    class LifecycleContract {

        @Test
        @DisplayName("declares neither framework template method: unload and reload are UltiToolsPlugin's final methods")
        void declaresNoTemplateMethodOverride() {
            // Control: the reflection really reads this class's own methods, so an empty result
            // below cannot pass vacuously.
            assertThat(Arrays.stream(UltiWorlds.class.getDeclaredMethods()).map(Method::getName))
                .contains("registerSelf", "supported");
            for (Method method : UltiWorlds.class.getDeclaredMethods()) {
                assertThat(method.getName())
                    .as("UltiWorlds must not declare %s", method)
                    .isNotIn("unregisterSelf", "reloadSelf");
            }
        }

        @Test
        @DisplayName("declares no onUnregister()/onReload() hook: the log-only overrides were deleted, not renamed")
        void declaresNoLifecycleHook() {
            for (Method method : UltiWorlds.class.getDeclaredMethods()) {
                assertThat(method.getName())
                    .as("UltiWorlds has no unload or reload work of its own, so must not declare %s", method)
                    .isNotIn("onUnregister", "onReload");
            }
        }

        @Test
        @DisplayName("unregisterSelf and reloadSelf resolve to UltiToolsPlugin's final methods")
        void templateMethodsResolveToFrameworkFinalMethods() throws Exception {
            for (String name : new String[] {"unregisterSelf", "reloadSelf"}) {
                Method method = UltiWorlds.class.getMethod(name);
                assertThat(method.getDeclaringClass())
                    .as("%s must be inherited from the framework", name)
                    .isEqualTo(UltiToolsPlugin.class);
                assertThat(Modifier.isFinal(method.getModifiers()))
                    .as("%s must be final in the framework", name)
                    .isTrue();
            }
        }
    }

    @Nested
    @DisplayName("Plugin Metadata")
    class PluginMetadata {

        @Test
        @DisplayName("supported should return zh and en")
        void supported() throws Exception {
            UltiWorlds plugin = mock(UltiWorlds.class);
            when(plugin.supported()).thenCallRealMethod();

            List<String> langs = plugin.supported();

            assertThat(langs).containsExactly("zh", "en");
        }
    }

    @Nested
    @DisplayName("Annotation Tests")
    class AnnotationTests {

        @Test
        @DisplayName("should have @UltiToolsModule annotation")
        void shouldHaveModuleAnnotation() {
            assertThat(UltiWorlds.class.isAnnotationPresent(
                com.ultikits.ultitools.annotations.UltiToolsModule.class
            )).isTrue();
        }

        @Test
        @DisplayName("should scan correct packages")
        void shouldScanCorrectPackages() {
            com.ultikits.ultitools.annotations.UltiToolsModule annotation =
                UltiWorlds.class.getAnnotation(
                    com.ultikits.ultitools.annotations.UltiToolsModule.class
                );

            assertThat(annotation.scanBasePackages()).contains("com.ultikits.plugins.worlds");
        }
    }
}
