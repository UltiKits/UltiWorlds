package com.ultikits.plugins.worlds.commands;

import com.ultikits.plugins.worlds.UltiWorldsTestHelper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code UltiKits/UltiWorlds#19}, gate-1 IN-01: the console's 30-second delete window is measured on
 * a monotonic clock. The wall clock follows NTP and manual changes, so a backwards step smaller than
 * the elapsed time would silently lengthen the window, and a large one would let a request survive
 * into a window it was never meant to reach.
 *
 * @author wisdomme
 * @version 2.0.0
 */
@DisplayName("/world delete console window uses a monotonic clock (UltiWorlds#19, IN-01)")
class WorldCommandClockTest {

    @BeforeEach
    void setUp() throws Exception {
        UltiWorldsTestHelper.setUp();
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiWorldsTestHelper.tearDown();
    }

    @Test
    @DisplayName("the default clock reads System.nanoTime in milliseconds, not the wall clock")
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration") // the clock is a private injection point
    void theDefaultClockIsMonotonic() throws Exception {
        Field field = WorldCommand.class.getDeclaredField("clock");
        field.setAccessible(true);
        LongSupplier clock = (LongSupplier) field.get(new WorldCommand());

        long before = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
        long value = clock.getAsLong();
        long after = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());

        assertThat(value).isBetween(before, after);
    }
}
