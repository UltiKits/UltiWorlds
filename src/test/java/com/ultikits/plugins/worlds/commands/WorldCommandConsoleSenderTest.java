package com.ultikits.plugins.worlds.commands;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;
import com.ultikits.plugins.worlds.config.WorldConfig;
import com.ultikits.plugins.worlds.service.WorldService;
import com.ultikits.ultitools.UltiTools;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.abstracts.command.validation.CmdTargetComposition;
import com.ultikits.ultitools.annotations.command.CmdMapping;
import com.ultikits.ultitools.annotations.command.CmdTarget;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.invocation.Invocation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code UltiKits/UltiWorlds#19}, console half: {@code /world} now admits the server console at
 * class level so that {@code /world delete} can reach it, and every other subcommand is marked
 * player-only again so that the console gains exactly one capability.
 *
 * <p>These tests go through the framework's real {@code BaseCommandExecutor#onCommand} dispatch --
 * method matching, the validator chain, parameter building -- not through a direct method call,
 * because the sender restriction is enforced there and a direct call would bypass it.
 *
 * <p>Every one of the 20 other mappings is walked, not sampled: {@link #everyMappingIsWalked()}
 * reads the {@code @CmdMapping} formats off the class and fails if this table and the class ever
 * disagree.
 *
 * @author wisdomme
 * @version 2.0.0
 */
@DisplayName("/world sender restriction: console reaches delete and nothing else (UltiWorlds#19)")
class WorldCommandConsoleSenderTest {

    /** The framework's in-game-only refusal key (SenderTypeValidator). */
    private static final String PLAYER_ONLY = "只有游戏内可以执行这个指令！";

    /** mapping format -> an argument vector that matches it. {@code delete} is the one console mapping. */
    private static final Map<String, String[]> OTHER_TWENTY = new TreeMap<>();

    static {
        OTHER_TWENTY.put("", new String[0]);
        OTHER_TWENTY.put("list", new String[]{"list"});
        OTHER_TWENTY.put("tp <world>", new String[]{"tp", "scratchw"});
        OTHER_TWENTY.put("wizard", new String[]{"wizard"});
        OTHER_TWENTY.put("create <name>", new String[]{"create", "scratchw"});
        OTHER_TWENTY.put("create <name> <type>", new String[]{"create", "scratchw", "NETHER"});
        OTHER_TWENTY.put("load <name>", new String[]{"load", "scratchw"});
        OTHER_TWENTY.put("unload <name>", new String[]{"unload", "scratchw"});
        OTHER_TWENTY.put("set <world> <option> <value>", new String[]{"set", "scratchw", "pvp", "true"});
        OTHER_TWENTY.put("protect <world>", new String[]{"protect", "scratchw"});
        OTHER_TWENTY.put("unprotect <world>", new String[]{"unprotect", "scratchw"});
        OTHER_TWENTY.put("block <world>", new String[]{"block", "scratchw"});
        OTHER_TWENTY.put("unblock <world>", new String[]{"unblock", "scratchw"});
        OTHER_TWENTY.put("setspawn", new String[]{"setspawn"});
        OTHER_TWENTY.put("difficulty <world> <level>", new String[]{"difficulty", "scratchw", "EASY"});
        OTHER_TWENTY.put("postcmd <world> add <command...>", new String[]{"postcmd", "scratchw", "add", "say", "hi"});
        OTHER_TWENTY.put("postcmd <world> list", new String[]{"postcmd", "scratchw", "list"});
        OTHER_TWENTY.put("postcmd <world> clear", new String[]{"postcmd", "scratchw", "clear"});
        OTHER_TWENTY.put("info", new String[]{"info"});
        OTHER_TWENTY.put("help", new String[]{"help"});
    }

    private UltiToolsPlugin plugin;
    private WorldService worldService;
    private UltiTools ultiTools;
    private Command bukkitCommand;

    @BeforeEach
    void setUp() throws Exception {
        UltiWorldsTestHelper.setUp();
        plugin = UltiWorldsTestHelper.getMockPlugin();
        worldService = mock(WorldService.class);
        WorldConfig config = UltiWorldsTestHelper.createDefaultConfig();
        when(worldService.getConfig()).thenReturn(config);
        ultiTools = mock(UltiTools.class);
        when(ultiTools.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));
        bukkitCommand = mock(Command.class);
        when(bukkitCommand.getName()).thenReturn("world");
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiWorldsTestHelper.tearDown();
    }

    static Stream<Arguments> otherTwenty() {
        return OTHER_TWENTY.entrySet().stream().map(e -> Arguments.of(e.getKey(), e.getValue()));
    }

    private WorldCommand newCommand() throws Exception {
        WorldCommand command = spy(new WorldCommand());
        UltiWorldsTestHelper.setField(command, "worldService", worldService);
        UltiWorldsTestHelper.setField(command, "plugin", plugin);
        return command;
    }

    private static ConsoleCommandSender console() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        when(console.hasPermission(anyString())).thenReturn(true);
        when(console.isOp()).thenReturn(true);
        when(console.getName()).thenReturn("CONSOLE");
        return console;
    }

    private static List<String> messagesTo(CommandSender sender) {
        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(sender, atLeast(0)).sendMessage(sent.capture());
        return sent.getAllValues();
    }

    private static List<String> invokedMethodNames(Object mock) {
        List<String> names = new ArrayList<>();
        for (Invocation invocation : mockingDetails(mock).getInvocations()) {
            names.add(invocation.getMethod().getName());
        }
        return names;
    }

    @Test
    @DisplayName("the table walks every @CmdMapping on WorldCommand except delete, and nothing else")
    void everyMappingIsWalked() {
        List<String> formats = Arrays.stream(WorldCommand.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(CmdMapping.class))
                .map(m -> m.getAnnotation(CmdMapping.class).format())
                .filter(f -> !"delete <name>".equals(f))
                .sorted()
                .collect(Collectors.toList());
        assertThat(formats).hasSize(20).containsExactlyElementsOf(OTHER_TWENTY.keySet());
    }

    @Test
    @DisplayName("the class admits both senders, delete carries no narrowing, and every other mapping is PLAYER")
    void annotationsAreWhatTheLoadTimeGateAccepts() {
        assertThat(WorldCommand.class.getAnnotation(CmdTarget.class).value())
                .isEqualTo(CmdTarget.CmdTargetType.BOTH);
        assertThat(CmdTargetComposition.check(WorldCommand.class)).isEmpty();
        for (Method method : WorldCommand.class.getDeclaredMethods()) {
            CmdMapping mapping = method.getAnnotation(CmdMapping.class);
            if (mapping == null) {
                continue;
            }
            CmdTarget target = method.getAnnotation(CmdTarget.class);
            if ("delete <name>".equals(mapping.format())) {
                assertThat(target).as("delete must not be narrowed").isNull();
            } else {
                assertThat(target).as(mapping.format()).isNotNull();
                assertThat(target.value()).as(mapping.format()).isEqualTo(CmdTarget.CmdTargetType.PLAYER);
            }
        }
    }

    @ParameterizedTest(name = "console: /world {0}")
    @MethodSource("otherTwenty")
    @DisplayName("the console is refused by every mapping except delete and touches no service")
    void consoleIsRefusedByEveryOtherMapping(String format, String[] args) throws Exception {
        try (MockedStatic<UltiTools> utStatic = mockStatic(UltiTools.class)) {
            utStatic.when(UltiTools::getInstance).thenReturn(ultiTools);
            WorldCommand command = newCommand();
            ConsoleCommandSender console = console();

            command.onCommand(console, bukkitCommand, "world", args);

            List<String> sent = messagesTo(console);
            assertThat(mockingDetails(worldService).getInvocations()).as("service calls").isEmpty();
            assertThat(invokedMethodNames(command)).doesNotContain("executeCommand");
            if ("help".equals(format)) {
                // The literal "help" argument never reaches method matching: the framework answers
                // it with handleHelp after its sender gate, and that gate reads the class-level
                // target, which now admits the console. So the console is answered with the one
                // line it can use, and never with the player help.
                assertThat(sent).containsExactly("help.header", "help.delete_console");
            } else {
                assertThat(sent).containsExactly(PLAYER_ONLY);
            }
        }
    }

    @ParameterizedTest(name = "player: /world {0}")
    @MethodSource("otherTwenty")
    @DisplayName("control: a player with the same arguments is not refused by the sender gate")
    void aPlayerIsStillAdmittedByEveryOtherMapping(String format, String[] args) throws Exception {
        try (MockedStatic<UltiTools> utStatic = mockStatic(UltiTools.class)) {
            utStatic.when(UltiTools::getInstance).thenReturn(ultiTools);
            WorldCommand command = newCommand();
            Player player = UltiWorldsTestHelper.createMockPlayer("Admin", UUID.randomUUID());

            command.onCommand(player, bukkitCommand, "world", args);

            assertThat(messagesTo(player)).doesNotContain(PLAYER_ONLY);
            if ("help".equals(format)) {
                assertThat(messagesTo(player)).contains("help.header", "help.delete").doesNotContain("help.delete_console");
            } else {
                assertThat(invokedMethodNames(command)).contains("executeCommand");
            }
        }
    }

    @Test
    @DisplayName("positive control: the console's /world delete passes the sender gate and reaches the command")
    void theConsoleReachesDelete() throws Exception {
        try (MockedStatic<UltiTools> utStatic = mockStatic(UltiTools.class)) {
            utStatic.when(UltiTools::getInstance).thenReturn(ultiTools);
            WorldCommand command = newCommand();
            ConsoleCommandSender console = console();

            command.onCommand(console, bukkitCommand, "world", new String[]{"delete", "scratchw"});

            assertThat(messagesTo(console)).doesNotContain(PLAYER_ONLY);
            assertThat(invokedMethodNames(command)).contains("executeCommand");
        }
    }
}
