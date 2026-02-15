package com.ultikits.plugins.worlds.conversation;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;
import com.ultikits.plugins.worlds.service.WorldService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldType;
import org.bukkit.conversations.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for WorldCreateConversation and its inner prompt classes.
 * Uses reflection to access private inner classes.
 *
 * @author wisdomme
 * @version 2.0.0
 */
@DisplayName("WorldCreateConversation Tests")
class WorldCreateConversationTest {

    private WorldService mockWorldService;
    private UltiToolsPlugin mockPlugin;
    private WorldCreateConversation conversation;

    @BeforeEach
    void setUp() throws Exception {
        UltiWorldsTestHelper.setUp();
        mockPlugin = UltiWorldsTestHelper.getMockPlugin();
        mockWorldService = mock(WorldService.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiWorldsTestHelper.tearDown();
    }

    /**
     * Helper to create inner prompt instance via reflection.
     */
    private Object createInnerPrompt(String className) throws Exception {
        Class<?>[] innerClasses = WorldCreateConversation.class.getDeclaredClasses();
        for (Class<?> inner : innerClasses) {
            if (inner.getSimpleName().equals(className)) {
                Constructor<?> ctor = inner.getDeclaredConstructor(WorldCreateConversation.class);
                ctor.setAccessible(true); // NOPMD - test reflection
                return ctor.newInstance(conversation);
            }
        }
        throw new ClassNotFoundException("Inner class not found: " + className);
    }

    /**
     * Helper to invoke getPromptText on a prompt.
     */
    private String invokeGetPromptText(Object prompt, ConversationContext context) throws Exception {
        Method method = findMethod(prompt.getClass(), "getPromptText", ConversationContext.class);
        method.setAccessible(true); // NOPMD - test reflection
        return (String) method.invoke(prompt, context);
    }

    /**
     * Helper to invoke isInputValid on a ValidatingPrompt.
     */
    private boolean invokeIsInputValid(Object prompt, ConversationContext context, String input) throws Exception {
        Method method = findMethod(prompt.getClass(), "isInputValid", ConversationContext.class, String.class);
        method.setAccessible(true); // NOPMD - test reflection
        return (boolean) method.invoke(prompt, context, input);
    }

    /**
     * Helper to invoke getFailedValidationText on a ValidatingPrompt.
     */
    private String invokeGetFailedValidationText(Object prompt, ConversationContext context, String input) throws Exception {
        Method method = findMethod(prompt.getClass(), "getFailedValidationText", ConversationContext.class, String.class);
        method.setAccessible(true); // NOPMD - test reflection
        return (String) method.invoke(prompt, context, input);
    }

    /**
     * Helper to invoke acceptValidatedInput on a ValidatingPrompt.
     */
    private Prompt invokeAcceptValidatedInput(Object prompt, ConversationContext context, String input) throws Exception {
        Method method = findMethod(prompt.getClass(), "acceptValidatedInput", ConversationContext.class, String.class);
        method.setAccessible(true); // NOPMD - test reflection
        return (Prompt) method.invoke(prompt, context, input);
    }

    /**
     * Helper to invoke acceptInput on a StringPrompt.
     */
    private Prompt invokeAcceptInput(Object prompt, ConversationContext context, String input) throws Exception {
        Method method = findMethod(prompt.getClass(), "acceptInput", ConversationContext.class, String.class);
        method.setAccessible(true); // NOPMD - test reflection
        return (Prompt) method.invoke(prompt, context, input);
    }

    /**
     * Helper to invoke getNextPrompt on a MessagePrompt.
     */
    private Prompt invokeGetNextPrompt(Object prompt, ConversationContext context) throws Exception {
        Method method = findMethod(prompt.getClass(), "getNextPrompt", ConversationContext.class);
        method.setAccessible(true); // NOPMD - test reflection
        return (Prompt) method.invoke(prompt, context);
    }

    private Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) throws NoSuchMethodException {
        try {
            return clazz.getDeclaredMethod(name, paramTypes);
        } catch (NoSuchMethodException e) {
            if (clazz.getSuperclass() != null) {
                return findMethod(clazz.getSuperclass(), name, paramTypes);
            }
            throw e;
        }
    }

    @Nested
    @DisplayName("Constructor and Start")
    class ConstructorTests {

        @Test
        @DisplayName("constructor should initialize with worldService and plugin")
        void constructorInitialization() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                PluginManager pluginManager = mock(PluginManager.class);
                Plugin ultiToolsPlugin = mock(Plugin.class);
                bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
                when(pluginManager.getPlugin("UltiTools")).thenReturn(ultiToolsPlugin);

                conversation = new WorldCreateConversation(mockWorldService, mockPlugin);

                assertThat(conversation).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("WorldNamePrompt")
    class WorldNamePromptTests {

        @BeforeEach
        void init() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                PluginManager pluginManager = mock(PluginManager.class);
                Plugin ultiToolsPlugin = mock(Plugin.class);
                bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
                when(pluginManager.getPlugin("UltiTools")).thenReturn(ultiToolsPlugin);
                conversation = new WorldCreateConversation(mockWorldService, mockPlugin);
            }
        }

        @Test
        @DisplayName("getPromptText should return i18n key")
        void getPromptText() throws Exception {
            Object prompt = createInnerPrompt("WorldNamePrompt");
            ConversationContext context = mock(ConversationContext.class);

            String text = invokeGetPromptText(prompt, context);

            assertThat(text).isEqualTo("wizard.enter_name");
        }

        @Test
        @DisplayName("isInputValid should accept valid world name")
        void isInputValidAccepts() throws Exception {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getWorld("test_world")).thenReturn(null);

                Object prompt = createInnerPrompt("WorldNamePrompt");
                ConversationContext context = mock(ConversationContext.class);

                boolean valid = invokeIsInputValid(prompt, context, "test_world");

                assertThat(valid).isTrue();
            }
        }

        @Test
        @DisplayName("isInputValid should reject invalid characters")
        void isInputValidRejectsInvalid() throws Exception {
            Object prompt = createInnerPrompt("WorldNamePrompt");
            ConversationContext context = mock(ConversationContext.class);

            boolean valid = invokeIsInputValid(prompt, context, "test world!");

            assertThat(valid).isFalse();
        }

        @Test
        @DisplayName("isInputValid should reject existing world")
        void isInputValidRejectsExisting() throws Exception {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                World existingWorld = mock(World.class);
                bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(existingWorld);

                Object prompt = createInnerPrompt("WorldNamePrompt");
                ConversationContext context = mock(ConversationContext.class);

                boolean valid = invokeIsInputValid(prompt, context, "world");

                assertThat(valid).isFalse();
            }
        }

        @Test
        @DisplayName("getFailedValidationText should return invalid name message for bad pattern")
        void getFailedValidationTextInvalidPattern() throws Exception {
            Object prompt = createInnerPrompt("WorldNamePrompt");
            ConversationContext context = mock(ConversationContext.class);

            String text = invokeGetFailedValidationText(prompt, context, "bad world!");

            assertThat(text).isEqualTo("wizard.invalid_name");
        }

        @Test
        @DisplayName("getFailedValidationText should return world exists message for valid pattern")
        void getFailedValidationTextWorldExists() throws Exception {
            Object prompt = createInnerPrompt("WorldNamePrompt");
            ConversationContext context = mock(ConversationContext.class);

            String text = invokeGetFailedValidationText(prompt, context, "valid_name");

            assertThat(text).isEqualTo("wizard.world_exists");
        }

        @Test
        @DisplayName("acceptValidatedInput should store world name and return EnvironmentPrompt")
        void acceptValidatedInput() throws Exception {
            Object prompt = createInnerPrompt("WorldNamePrompt");
            ConversationContext context = mock(ConversationContext.class);

            Prompt next = invokeAcceptValidatedInput(prompt, context, "new_world");

            verify(context).setSessionData("world_name", "new_world");
            assertThat(next).isNotNull();
            assertThat(next.getClass().getSimpleName()).isEqualTo("EnvironmentPrompt");
        }
    }

    @Nested
    @DisplayName("EnvironmentPrompt")
    class EnvironmentPromptTests {

        @BeforeEach
        void init() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                PluginManager pluginManager = mock(PluginManager.class);
                Plugin ultiToolsPlugin = mock(Plugin.class);
                bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
                when(pluginManager.getPlugin("UltiTools")).thenReturn(ultiToolsPlugin);
                conversation = new WorldCreateConversation(mockWorldService, mockPlugin);
            }
        }

        @Test
        @DisplayName("getPromptText should contain environment options")
        void getPromptText() throws Exception {
            Object prompt = createInnerPrompt("EnvironmentPrompt");
            ConversationContext context = mock(ConversationContext.class);

            String text = invokeGetPromptText(prompt, context);

            assertThat(text).contains("wizard.select_environment");
        }

        @Test
        @DisplayName("isInputValid should accept '1' for NORMAL")
        void isInputValidAcceptsNumber() throws Exception {
            Object prompt = createInnerPrompt("EnvironmentPrompt");
            ConversationContext context = mock(ConversationContext.class);

            assertThat(invokeIsInputValid(prompt, context, "1")).isTrue();
        }

        @Test
        @DisplayName("isInputValid should accept 'normal' text")
        void isInputValidAcceptsText() throws Exception {
            Object prompt = createInnerPrompt("EnvironmentPrompt");
            ConversationContext context = mock(ConversationContext.class);

            assertThat(invokeIsInputValid(prompt, context, "normal")).isTrue();
        }

        @Test
        @DisplayName("isInputValid should accept 'nether'")
        void isInputValidAcceptsNether() throws Exception {
            Object prompt = createInnerPrompt("EnvironmentPrompt");
            ConversationContext context = mock(ConversationContext.class);

            assertThat(invokeIsInputValid(prompt, context, "nether")).isTrue();
        }

        @Test
        @DisplayName("isInputValid should accept 'the_end'")
        void isInputValidAcceptsEnd() throws Exception {
            Object prompt = createInnerPrompt("EnvironmentPrompt");
            ConversationContext context = mock(ConversationContext.class);

            assertThat(invokeIsInputValid(prompt, context, "the_end")).isTrue();
        }

        @Test
        @DisplayName("isInputValid should accept 'end'")
        void isInputValidAcceptsEndShort() throws Exception {
            Object prompt = createInnerPrompt("EnvironmentPrompt");
            ConversationContext context = mock(ConversationContext.class);

            assertThat(invokeIsInputValid(prompt, context, "end")).isTrue();
        }

        @Test
        @DisplayName("isInputValid should reject invalid input")
        void isInputValidRejectsInvalid() throws Exception {
            Object prompt = createInnerPrompt("EnvironmentPrompt");
            ConversationContext context = mock(ConversationContext.class);

            assertThat(invokeIsInputValid(prompt, context, "void")).isFalse();
        }

        @Test
        @DisplayName("getFailedValidationText should return invalid environment message")
        void getFailedValidationText() throws Exception {
            Object prompt = createInnerPrompt("EnvironmentPrompt");
            ConversationContext context = mock(ConversationContext.class);

            String text = invokeGetFailedValidationText(prompt, context, "invalid");

            assertThat(text).isEqualTo("wizard.invalid_environment");
        }

        @Test
        @DisplayName("acceptValidatedInput should store environment and return WorldTypePrompt")
        void acceptValidatedInput() throws Exception {
            Object prompt = createInnerPrompt("EnvironmentPrompt");
            ConversationContext context = mock(ConversationContext.class);

            Prompt next = invokeAcceptValidatedInput(prompt, context, "2");

            verify(context).setSessionData("environment", World.Environment.NETHER);
            assertThat(next.getClass().getSimpleName()).isEqualTo("WorldTypePrompt");
        }
    }

    @Nested
    @DisplayName("WorldTypePrompt")
    class WorldTypePromptTests {

        @BeforeEach
        void init() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                PluginManager pluginManager = mock(PluginManager.class);
                Plugin ultiToolsPlugin = mock(Plugin.class);
                bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
                when(pluginManager.getPlugin("UltiTools")).thenReturn(ultiToolsPlugin);
                conversation = new WorldCreateConversation(mockWorldService, mockPlugin);
            }
        }

        @Test
        @DisplayName("getPromptText should contain type options")
        void getPromptText() throws Exception {
            Object prompt = createInnerPrompt("WorldTypePrompt");
            ConversationContext context = mock(ConversationContext.class);

            String text = invokeGetPromptText(prompt, context);

            assertThat(text).contains("wizard.select_type");
        }

        @Test
        @DisplayName("isInputValid should accept '1' for NORMAL")
        void isInputValidNormal() throws Exception {
            Object prompt = createInnerPrompt("WorldTypePrompt");
            ConversationContext context = mock(ConversationContext.class);

            assertThat(invokeIsInputValid(prompt, context, "1")).isTrue();
        }

        @Test
        @DisplayName("isInputValid should accept 'flat'")
        void isInputValidFlat() throws Exception {
            Object prompt = createInnerPrompt("WorldTypePrompt");
            ConversationContext context = mock(ConversationContext.class);

            assertThat(invokeIsInputValid(prompt, context, "flat")).isTrue();
        }

        @Test
        @DisplayName("isInputValid should accept '3' for AMPLIFIED")
        void isInputValidAmplified() throws Exception {
            Object prompt = createInnerPrompt("WorldTypePrompt");
            ConversationContext context = mock(ConversationContext.class);

            assertThat(invokeIsInputValid(prompt, context, "3")).isTrue();
        }

        @Test
        @DisplayName("isInputValid should accept 'large_biomes'")
        void isInputValidLargeBiomes() throws Exception {
            Object prompt = createInnerPrompt("WorldTypePrompt");
            ConversationContext context = mock(ConversationContext.class);

            assertThat(invokeIsInputValid(prompt, context, "large_biomes")).isTrue();
        }

        @Test
        @DisplayName("isInputValid should accept 'large' shorthand")
        void isInputValidLargeShort() throws Exception {
            Object prompt = createInnerPrompt("WorldTypePrompt");
            ConversationContext context = mock(ConversationContext.class);

            assertThat(invokeIsInputValid(prompt, context, "large")).isTrue();
        }

        @Test
        @DisplayName("isInputValid should reject invalid type")
        void isInputValidRejects() throws Exception {
            Object prompt = createInnerPrompt("WorldTypePrompt");
            ConversationContext context = mock(ConversationContext.class);

            assertThat(invokeIsInputValid(prompt, context, "custom")).isFalse();
        }

        @Test
        @DisplayName("getFailedValidationText should return invalid type message")
        void getFailedValidationText() throws Exception {
            Object prompt = createInnerPrompt("WorldTypePrompt");
            ConversationContext context = mock(ConversationContext.class);

            String text = invokeGetFailedValidationText(prompt, context, "invalid");

            assertThat(text).isEqualTo("wizard.invalid_type");
        }

        @Test
        @DisplayName("acceptValidatedInput should store type and return StructuresPrompt")
        void acceptValidatedInput() throws Exception {
            Object prompt = createInnerPrompt("WorldTypePrompt");
            ConversationContext context = mock(ConversationContext.class);

            Prompt next = invokeAcceptValidatedInput(prompt, context, "flat");

            verify(context).setSessionData("world_type", WorldType.FLAT);
            assertThat(next.getClass().getSimpleName()).isEqualTo("StructuresPrompt");
        }
    }

    @Nested
    @DisplayName("StructuresPrompt")
    class StructuresPromptTests {

        @BeforeEach
        void init() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                PluginManager pluginManager = mock(PluginManager.class);
                Plugin ultiToolsPlugin = mock(Plugin.class);
                bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
                when(pluginManager.getPlugin("UltiTools")).thenReturn(ultiToolsPlugin);
                conversation = new WorldCreateConversation(mockWorldService, mockPlugin);
            }
        }

        @Test
        @DisplayName("getPromptText should contain structures question")
        void getPromptText() throws Exception {
            Object prompt = createInnerPrompt("StructuresPrompt");
            ConversationContext context = mock(ConversationContext.class);

            String text = invokeGetPromptText(prompt, context);

            assertThat(text).contains("wizard.generate_structures");
        }

        @Test
        @DisplayName("isInputValid should accept 'y'")
        void isInputValidY() throws Exception {
            Object prompt = createInnerPrompt("StructuresPrompt");
            ConversationContext context = mock(ConversationContext.class);

            assertThat(invokeIsInputValid(prompt, context, "y")).isTrue();
        }

        @Test
        @DisplayName("isInputValid should accept 'yes'")
        void isInputValidYes() throws Exception {
            Object prompt = createInnerPrompt("StructuresPrompt");
            ConversationContext context = mock(ConversationContext.class);

            assertThat(invokeIsInputValid(prompt, context, "yes")).isTrue();
        }

        @Test
        @DisplayName("isInputValid should accept 'n'")
        void isInputValidN() throws Exception {
            Object prompt = createInnerPrompt("StructuresPrompt");
            ConversationContext context = mock(ConversationContext.class);

            assertThat(invokeIsInputValid(prompt, context, "n")).isTrue();
        }

        @Test
        @DisplayName("isInputValid should accept 'no'")
        void isInputValidNo() throws Exception {
            Object prompt = createInnerPrompt("StructuresPrompt");
            ConversationContext context = mock(ConversationContext.class);

            assertThat(invokeIsInputValid(prompt, context, "no")).isTrue();
        }

        @Test
        @DisplayName("isInputValid should accept 'true'")
        void isInputValidTrue() throws Exception {
            Object prompt = createInnerPrompt("StructuresPrompt");
            ConversationContext context = mock(ConversationContext.class);

            assertThat(invokeIsInputValid(prompt, context, "true")).isTrue();
        }

        @Test
        @DisplayName("isInputValid should accept Chinese yes")
        void isInputValidChinese() throws Exception {
            Object prompt = createInnerPrompt("StructuresPrompt");
            ConversationContext context = mock(ConversationContext.class);

            assertThat(invokeIsInputValid(prompt, context, "\u662f")).isTrue();
        }

        @Test
        @DisplayName("isInputValid should reject invalid input")
        void isInputValidRejects() throws Exception {
            Object prompt = createInnerPrompt("StructuresPrompt");
            ConversationContext context = mock(ConversationContext.class);

            assertThat(invokeIsInputValid(prompt, context, "maybe")).isFalse();
        }

        @Test
        @DisplayName("getFailedValidationText should return invalid yn message")
        void getFailedValidationText() throws Exception {
            Object prompt = createInnerPrompt("StructuresPrompt");
            ConversationContext context = mock(ConversationContext.class);

            String text = invokeGetFailedValidationText(prompt, context, "maybe");

            assertThat(text).isEqualTo("wizard.invalid_yn");
        }

        @Test
        @DisplayName("acceptValidatedInput should store true for 'y' and return SeedPrompt")
        void acceptValidatedInputYes() throws Exception {
            Object prompt = createInnerPrompt("StructuresPrompt");
            ConversationContext context = mock(ConversationContext.class);

            Prompt next = invokeAcceptValidatedInput(prompt, context, "y");

            verify(context).setSessionData("generate_structures", true);
            assertThat(next.getClass().getSimpleName()).isEqualTo("SeedPrompt");
        }

        @Test
        @DisplayName("acceptValidatedInput should store false for 'n'")
        void acceptValidatedInputNo() throws Exception {
            Object prompt = createInnerPrompt("StructuresPrompt");
            ConversationContext context = mock(ConversationContext.class);

            Prompt next = invokeAcceptValidatedInput(prompt, context, "n");

            verify(context).setSessionData("generate_structures", false);
            assertThat(next.getClass().getSimpleName()).isEqualTo("SeedPrompt");
        }
    }

    @Nested
    @DisplayName("SeedPrompt")
    class SeedPromptTests {

        @BeforeEach
        void init() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                PluginManager pluginManager = mock(PluginManager.class);
                Plugin ultiToolsPlugin = mock(Plugin.class);
                bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
                when(pluginManager.getPlugin("UltiTools")).thenReturn(ultiToolsPlugin);
                conversation = new WorldCreateConversation(mockWorldService, mockPlugin);
            }
        }

        @Test
        @DisplayName("getPromptText should return seed prompt message")
        void getPromptText() throws Exception {
            Object prompt = createInnerPrompt("SeedPrompt");
            ConversationContext context = mock(ConversationContext.class);

            String text = invokeGetPromptText(prompt, context);

            assertThat(text).isEqualTo("wizard.enter_seed");
        }

        @Test
        @DisplayName("acceptInput should store seed and return ConfirmPrompt")
        void acceptInputWithSeed() throws Exception {
            Object prompt = createInnerPrompt("SeedPrompt");
            ConversationContext context = mock(ConversationContext.class);

            Prompt next = invokeAcceptInput(prompt, context, "12345");

            verify(context).setSessionData("seed", "12345");
            assertThat(next.getClass().getSimpleName()).isEqualTo("ConfirmPrompt");
        }

        @Test
        @DisplayName("acceptInput should not store seed for 'random'")
        void acceptInputRandom() throws Exception {
            Object prompt = createInnerPrompt("SeedPrompt");
            ConversationContext context = mock(ConversationContext.class);

            Prompt next = invokeAcceptInput(prompt, context, "random");

            verify(context, never()).setSessionData(eq("seed"), anyString());
            assertThat(next.getClass().getSimpleName()).isEqualTo("ConfirmPrompt");
        }

        @Test
        @DisplayName("acceptInput should not store seed for empty input")
        void acceptInputEmpty() throws Exception {
            Object prompt = createInnerPrompt("SeedPrompt");
            ConversationContext context = mock(ConversationContext.class);

            Prompt next = invokeAcceptInput(prompt, context, "");

            verify(context, never()).setSessionData(eq("seed"), anyString());
            assertThat(next.getClass().getSimpleName()).isEqualTo("ConfirmPrompt");
        }

        @Test
        @DisplayName("acceptInput should not store seed for null input")
        void acceptInputNull() throws Exception {
            Object prompt = createInnerPrompt("SeedPrompt");
            ConversationContext context = mock(ConversationContext.class);

            Prompt next = invokeAcceptInput(prompt, context, null);

            verify(context, never()).setSessionData(eq("seed"), anyString());
            assertThat(next.getClass().getSimpleName()).isEqualTo("ConfirmPrompt");
        }
    }

    @Nested
    @DisplayName("ConfirmPrompt")
    class ConfirmPromptTests {

        @BeforeEach
        void init() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                PluginManager pluginManager = mock(PluginManager.class);
                Plugin ultiToolsPlugin = mock(Plugin.class);
                bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
                when(pluginManager.getPlugin("UltiTools")).thenReturn(ultiToolsPlugin);
                conversation = new WorldCreateConversation(mockWorldService, mockPlugin);
            }
        }

        @Test
        @DisplayName("getPromptText should contain confirmation details")
        void getPromptText() throws Exception {
            Object prompt = createInnerPrompt("ConfirmPrompt");
            ConversationContext context = mock(ConversationContext.class);
            when(context.getSessionData("world_name")).thenReturn("test_world");
            when(context.getSessionData("environment")).thenReturn(World.Environment.NORMAL);
            when(context.getSessionData("world_type")).thenReturn(WorldType.NORMAL);
            when(context.getSessionData("generate_structures")).thenReturn(true);
            when(context.getSessionData("seed")).thenReturn(null);

            String text = invokeGetPromptText(prompt, context);

            assertThat(text).contains("wizard.confirm_header");
            assertThat(text).contains("test_world");
        }

        @Test
        @DisplayName("getPromptText should show seed when provided")
        void getPromptTextWithSeed() throws Exception {
            Object prompt = createInnerPrompt("ConfirmPrompt");
            ConversationContext context = mock(ConversationContext.class);
            when(context.getSessionData("world_name")).thenReturn("test_world");
            when(context.getSessionData("environment")).thenReturn(World.Environment.NETHER);
            when(context.getSessionData("world_type")).thenReturn(WorldType.FLAT);
            when(context.getSessionData("generate_structures")).thenReturn(false);
            when(context.getSessionData("seed")).thenReturn("12345");

            String text = invokeGetPromptText(prompt, context);

            assertThat(text).contains("12345");
        }

        @Test
        @DisplayName("isInputValid should accept 'y'")
        void isInputValidY() throws Exception {
            Object prompt = createInnerPrompt("ConfirmPrompt");
            ConversationContext context = mock(ConversationContext.class);

            assertThat(invokeIsInputValid(prompt, context, "y")).isTrue();
        }

        @Test
        @DisplayName("isInputValid should accept 'confirm'")
        void isInputValidConfirm() throws Exception {
            Object prompt = createInnerPrompt("ConfirmPrompt");
            ConversationContext context = mock(ConversationContext.class);

            assertThat(invokeIsInputValid(prompt, context, "confirm")).isTrue();
        }

        @Test
        @DisplayName("isInputValid should accept 'cancel'")
        void isInputValidCancel() throws Exception {
            Object prompt = createInnerPrompt("ConfirmPrompt");
            ConversationContext context = mock(ConversationContext.class);

            assertThat(invokeIsInputValid(prompt, context, "cancel")).isTrue();
        }

        @Test
        @DisplayName("isInputValid should reject invalid input")
        void isInputValidRejects() throws Exception {
            Object prompt = createInnerPrompt("ConfirmPrompt");
            ConversationContext context = mock(ConversationContext.class);

            assertThat(invokeIsInputValid(prompt, context, "maybe")).isFalse();
        }

        @Test
        @DisplayName("getFailedValidationText should return invalid yn message")
        void getFailedValidationText() throws Exception {
            Object prompt = createInnerPrompt("ConfirmPrompt");
            ConversationContext context = mock(ConversationContext.class);

            String text = invokeGetFailedValidationText(prompt, context, "maybe");

            assertThat(text).isEqualTo("wizard.invalid_yn");
        }

        @Test
        @DisplayName("acceptValidatedInput should return CreateWorldPrompt for 'yes'")
        void acceptValidatedInputYes() throws Exception {
            Object prompt = createInnerPrompt("ConfirmPrompt");
            ConversationContext context = mock(ConversationContext.class);

            Prompt next = invokeAcceptValidatedInput(prompt, context, "yes");

            assertThat(next).isNotNull();
            assertThat(next.getClass().getSimpleName()).isEqualTo("CreateWorldPrompt");
        }

        @Test
        @DisplayName("acceptValidatedInput should end conversation and send cancel message for 'no'")
        void acceptValidatedInputNo() throws Exception {
            Object prompt = createInnerPrompt("ConfirmPrompt");
            Conversable conversable = mock(Conversable.class);
            ConversationContext context = mock(ConversationContext.class);
            when(context.getForWhom()).thenReturn(conversable);

            Prompt next = invokeAcceptValidatedInput(prompt, context, "no");

            assertThat(next).isEqualTo(Prompt.END_OF_CONVERSATION);
            verify(conversable).sendRawMessage(anyString());
        }
    }

    @Nested
    @DisplayName("CreateWorldPrompt")
    class CreateWorldPromptTests {

        @BeforeEach
        void init() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                PluginManager pluginManager = mock(PluginManager.class);
                Plugin ultiToolsPlugin = mock(Plugin.class);
                bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
                when(pluginManager.getPlugin("UltiTools")).thenReturn(ultiToolsPlugin);
                conversation = new WorldCreateConversation(mockWorldService, mockPlugin);
            }
        }

        @Test
        @DisplayName("getPromptText should return creating message")
        void getPromptText() throws Exception {
            Object prompt = createInnerPrompt("CreateWorldPrompt");
            ConversationContext context = mock(ConversationContext.class);

            String text = invokeGetPromptText(prompt, context);

            assertThat(text).isEqualTo("wizard.creating");
        }

        @Test
        @DisplayName("getNextPrompt should return END_OF_CONVERSATION and schedule world creation")
        void getNextPrompt() throws Exception {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
                bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

                Object prompt = createInnerPrompt("CreateWorldPrompt");
                ConversationContext context = mock(ConversationContext.class);
                when(context.getSessionData("world_name")).thenReturn("new_world");
                when(context.getSessionData("environment")).thenReturn(World.Environment.NORMAL);
                when(context.getSessionData("world_type")).thenReturn(WorldType.NORMAL);
                when(context.getSessionData("generate_structures")).thenReturn(true);
                when(context.getSessionData("seed")).thenReturn(null);

                Prompt next = invokeGetNextPrompt(prompt, context);

                assertThat(next).isEqualTo(Prompt.END_OF_CONVERSATION);
                verify(scheduler).runTask(any(Plugin.class), any(Runnable.class));
            }
        }
    }

    @Nested
    @DisplayName("ConversationAbandoned")
    class ConversationAbandonedTests {

        @BeforeEach
        void init() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                PluginManager pluginManager = mock(PluginManager.class);
                Plugin ultiToolsPlugin = mock(Plugin.class);
                bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
                when(pluginManager.getPlugin("UltiTools")).thenReturn(ultiToolsPlugin);
                conversation = new WorldCreateConversation(mockWorldService, mockPlugin);
            }
        }

        @Test
        @DisplayName("conversationAbandoned should send timeout message on inactivity")
        void conversationAbandonedTimeout() {
            ConversationAbandonedEvent event = mock(ConversationAbandonedEvent.class);
            when(event.gracefulExit()).thenReturn(false);
            when(event.getCanceller()).thenReturn(mock(InactivityConversationCanceller.class));

            Conversable conversable = mock(Conversable.class);
            ConversationContext context = mock(ConversationContext.class);
            when(event.getContext()).thenReturn(context);
            when(context.getForWhom()).thenReturn(conversable);

            conversation.conversationAbandoned(event);

            verify(conversable).sendRawMessage("wizard.timeout");
        }

        @Test
        @DisplayName("conversationAbandoned should send cancelled message for other reasons")
        void conversationAbandonedCancelled() {
            ConversationAbandonedEvent event = mock(ConversationAbandonedEvent.class);
            when(event.gracefulExit()).thenReturn(false);
            when(event.getCanceller()).thenReturn(mock(ConversationCanceller.class));

            Conversable conversable = mock(Conversable.class);
            ConversationContext context = mock(ConversationContext.class);
            when(event.getContext()).thenReturn(context);
            when(context.getForWhom()).thenReturn(conversable);

            conversation.conversationAbandoned(event);

            verify(conversable).sendRawMessage("wizard.cancelled");
        }

        @Test
        @DisplayName("conversationAbandoned should do nothing on graceful exit")
        void conversationAbandonedGraceful() {
            ConversationAbandonedEvent event = mock(ConversationAbandonedEvent.class);
            when(event.gracefulExit()).thenReturn(true);

            conversation.conversationAbandoned(event);

            verify(event, never()).getContext();
        }
    }

    @Nested
    @DisplayName("Helper Methods")
    class HelperMethods {

        @BeforeEach
        void init() {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                PluginManager pluginManager = mock(PluginManager.class);
                Plugin ultiToolsPlugin = mock(Plugin.class);
                bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
                when(pluginManager.getPlugin("UltiTools")).thenReturn(ultiToolsPlugin);
                conversation = new WorldCreateConversation(mockWorldService, mockPlugin);
            }
        }

        @Test
        @DisplayName("getEnvironmentName should return correct name for NORMAL")
        void getEnvironmentNameNormal() throws Exception {
            Method method = WorldCreateConversation.class.getDeclaredMethod("getEnvironmentName", World.Environment.class);
            method.setAccessible(true); // NOPMD - test reflection

            String name = (String) method.invoke(conversation, World.Environment.NORMAL);
            assertThat(name).isEqualTo("environment.normal");
        }

        @Test
        @DisplayName("getEnvironmentName should return correct name for NETHER")
        void getEnvironmentNameNether() throws Exception {
            Method method = WorldCreateConversation.class.getDeclaredMethod("getEnvironmentName", World.Environment.class);
            method.setAccessible(true); // NOPMD - test reflection

            String name = (String) method.invoke(conversation, World.Environment.NETHER);
            assertThat(name).isEqualTo("environment.nether");
        }

        @Test
        @DisplayName("getEnvironmentName should return correct name for THE_END")
        void getEnvironmentNameEnd() throws Exception {
            Method method = WorldCreateConversation.class.getDeclaredMethod("getEnvironmentName", World.Environment.class);
            method.setAccessible(true); // NOPMD - test reflection

            String name = (String) method.invoke(conversation, World.Environment.THE_END);
            assertThat(name).isEqualTo("environment.the_end");
        }

        @Test
        @DisplayName("getTypeName should return correct name for NORMAL")
        void getTypeNameNormal() throws Exception {
            Method method = WorldCreateConversation.class.getDeclaredMethod("getTypeName", WorldType.class);
            method.setAccessible(true); // NOPMD - test reflection

            String name = (String) method.invoke(conversation, WorldType.NORMAL);
            assertThat(name).isEqualTo("type.normal");
        }

        @Test
        @DisplayName("getTypeName should return correct name for FLAT")
        void getTypeNameFlat() throws Exception {
            Method method = WorldCreateConversation.class.getDeclaredMethod("getTypeName", WorldType.class);
            method.setAccessible(true); // NOPMD - test reflection

            String name = (String) method.invoke(conversation, WorldType.FLAT);
            assertThat(name).isEqualTo("type.flat");
        }

        @Test
        @DisplayName("getTypeName should return correct name for AMPLIFIED")
        void getTypeNameAmplified() throws Exception {
            Method method = WorldCreateConversation.class.getDeclaredMethod("getTypeName", WorldType.class);
            method.setAccessible(true); // NOPMD - test reflection

            String name = (String) method.invoke(conversation, WorldType.AMPLIFIED);
            assertThat(name).isEqualTo("type.amplified");
        }

        @Test
        @DisplayName("getTypeName should return correct name for LARGE_BIOMES")
        void getTypeNameLargeBiomes() throws Exception {
            Method method = WorldCreateConversation.class.getDeclaredMethod("getTypeName", WorldType.class);
            method.setAccessible(true); // NOPMD - test reflection

            String name = (String) method.invoke(conversation, WorldType.LARGE_BIOMES);
            assertThat(name).isEqualTo("type.large_biomes");
        }
    }
}
