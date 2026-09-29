package com.ultikits.plugins.worlds.testutil;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.mockbukkit.mockbukkit.exception.UnimplementedOperationException;

import java.lang.reflect.Method;

/**
 * Turns a MockBukkit {@link UnimplementedOperationException} into a test failure.
 * <p>
 * That exception extends JUnit's {@code TestAbortedException}, so a test that reaches a server
 * method MockBukkit does not implement was reported as <em>skipped</em>: green, with no assertion
 * run, and visible only as a number in the build's skipped count (UltiKits/UltiWorlds#33, #35).
 * Registered for every test of this module through {@code junit-platform.properties}, so the
 * skipped count cannot hide such a case again: it fails, naming the method to stub.
 */
public class UnimplementedIsFailure implements InvocationInterceptor {

    @Override
    public void interceptTestMethod(Invocation<Void> invocation,
                                    ReflectiveInvocationContext<Method> invocationContext,
                                    ExtensionContext extensionContext) throws Throwable {
        failInstead(invocation);
    }

    @Override
    public void interceptTestTemplateMethod(Invocation<Void> invocation,
                                            ReflectiveInvocationContext<Method> invocationContext,
                                            ExtensionContext extensionContext) throws Throwable {
        failInstead(invocation);
    }

    @Override
    public void interceptBeforeEachMethod(Invocation<Void> invocation,
                                          ReflectiveInvocationContext<Method> invocationContext,
                                          ExtensionContext extensionContext) throws Throwable {
        failInstead(invocation);
    }

    private static void failInstead(Invocation<Void> invocation) throws Throwable {
        try {
            invocation.proceed();
        } catch (UnimplementedOperationException e) {
            throw new AssertionError("The test reached a server method MockBukkit does not implement, which "
                    + "would have been reported as skipped; stub it so the test runs its assertions", e);
        }
    }
}
