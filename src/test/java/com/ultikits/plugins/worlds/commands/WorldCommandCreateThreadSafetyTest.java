package com.ultikits.plugins.worlds.commands;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;
import com.ultikits.plugins.worlds.config.WorldConfig;
import com.ultikits.plugins.worlds.service.WorldService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.command.RunAsync;

import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bukkit.Bukkit.getWorld;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Falsification test for FIX-01b: both {@code /world create} overloads must run on the primary
 * thread, because {@code WorldService.createWorld(...)} constructs a real {@link WorldCreator},
 * and world construction is main-thread-and-blocking by the platform's own contract.
 * <p>
 * This test does not hardcode "sync" or "async" dispatch -- it reads {@link RunAsync}'s presence
 * off the target method via reflection and dispatches accordingly, exactly like
 * {@code BaseCommandExecutor.executeCommand} does. That means this single, unmodified test file
 * is red while the annotation is present (dispatched via
 * {@link BukkitRunnable#runTaskAsynchronously}, which trips MockBukkit's own
 * {@code AsyncCatcher.catchOp} the same way Paper's real one would) and green once the annotation
 * is removed (dispatched via {@link BukkitRunnable#runTask}, deferred one tick like every other
 * synchronous command body).
 * <p>
 * {@code mockWorldService.createWorld(...)} is stubbed to call through to a REAL
 * {@link WorldCreator#createWorld()} against the module's own bootstrapped live server (rather
 * than returning a canned boolean), because the crash this test exists to catch happens inside
 * that real Bukkit/MockBukkit call chain ({@code ServerMock.createWorld} -&gt;
 * {@code ServerMock.addWorld} -&gt; {@code AsyncCatcher.catchOp("world add")}), not inside
 * {@code WorldCommand} itself.
 */
@DisplayName("WorldCommand create-world thread affinity")
class WorldCommandCreateThreadSafetyTest {

    private WorldCommand command;
    private WorldService mockWorldService;
    private Plugin schedulerPlugin;
    private Player player;

    @BeforeEach
    void setUp() throws Exception {
        UltiWorldsTestHelper.setUp();
        UltiToolsPlugin mockPlugin = UltiWorldsTestHelper.getMockPlugin();

        // A real, registered, enabled plugin -- required by BukkitRunnable.runTask/
        // runTaskAsynchronously, which the module's own mocked UltiToolsPlugin cannot satisfy
        // since it is a plain Mockito mock, not a Bukkit Plugin registered with the server.
        schedulerPlugin = MockBukkit.createMockPlugin("UltiWorldsThreadSafetyProbe");

        command = new WorldCommand();
        mockWorldService = mock(WorldService.class);
        WorldConfig mockConfig = UltiWorldsTestHelper.createDefaultConfig();

        UltiWorldsTestHelper.setField(command, "worldService", mockWorldService);
        UltiWorldsTestHelper.setField(command, "plugin", mockPlugin);

        when(mockWorldService.getConfig()).thenReturn(mockConfig);

        // Real world construction, exercising the exact Bukkit call chain that must run on the
        // primary thread -- see the class javadoc.
        when(mockWorldService.createWorld(any(), any(), any(), any())).thenAnswer(invocation -> {
            String name = invocation.getArgument(0);
            org.bukkit.World.Environment environment = invocation.getArgument(1);
            org.bukkit.WorldType type = invocation.getArgument(2);
            WorldCreator creator = new WorldCreator(name);
            creator.environment(environment);
            creator.type(type);
            return creator.createWorld() != null;
        });

        player = UltiWorldsTestHelper.createMockPlayer("ThreadSafetyProbe", UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiWorldsTestHelper.tearDown();
    }

    @Test
    @DisplayName("createWorld runs on the primary thread -- red before the fix (@RunAsync present), green after (removed)")
    void createWorldRunsOnThePrimaryThread() throws Exception {
        dispatchAndAssertPrimaryThread("createWorld", new Class<?>[]{Player.class, String.class},
                new Object[]{player, "uw_thread_probe_create"}, "uw_thread_probe_create");
    }

    @Test
    @DisplayName("createWorldWithType runs on the primary thread -- red before the fix (@RunAsync present), green after (removed)")
    void createWorldWithTypeRunsOnThePrimaryThread() throws Exception {
        dispatchAndAssertPrimaryThread("createWorldWithType",
                new Class<?>[]{Player.class, String.class, String.class},
                new Object[]{player, "uw_thread_probe_create_type", "NORMAL"},
                "uw_thread_probe_create_type");
    }

    /**
     * Dispatches the named {@link WorldCommand} method exactly the way
     * {@code BaseCommandExecutor.executeCommand} dispatches it: async
     * ({@link BukkitRunnable#runTaskAsynchronously}) if {@link RunAsync} is present on the
     * method, sync ({@link BukkitRunnable#runTask}, then one scheduler tick) otherwise.
     */
    private void dispatchAndAssertPrimaryThread(String methodName, Class<?>[] paramTypes,
                                                 Object[] args, String worldName) throws Exception {
        Method method = WorldCommand.class.getDeclaredMethod(methodName, paramTypes);
        boolean isAsync = method.isAnnotationPresent(RunAsync.class);

        AtomicReference<Throwable> thrown = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    method.invoke(command, args);
                } catch (InvocationTargetException e) {
                    thrown.set(e.getCause());
                } catch (Throwable t) {
                    thrown.set(t);
                } finally {
                    latch.countDown();
                }
            }
        };

        if (isAsync) {
            runnable.runTaskAsynchronously(schedulerPlugin);
        } else {
            runnable.runTask(schedulerPlugin);
            MockBukkit.getMock().getScheduler().performOneTick();
        }

        assertThat(latch.await(5, TimeUnit.SECONDS))
                .as("the dispatched command body must complete within the timeout")
                .isTrue();

        if (thrown.get() != null) {
            throw new AssertionError("command body threw: " + thrown.get(), thrown.get());
        }

        assertThat(getWorld(worldName))
                .as("world '%s' must exist after a successful create dispatch", worldName)
                .isNotNull();
    }
}
