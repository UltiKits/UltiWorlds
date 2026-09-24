package com.ultikits.plugins.worlds.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code UltiKits/UltiWorlds#18}: {@code WorldListGUI} was the predecessor of {@link WorldListPage}
 * and was never constructed by any command, listener or other class in this module. It is deleted
 * rather than wired, because {@link WorldListPage} already is the world list that bare
 * {@code /world} opens.
 *
 * <p>An unreachable class has no behaviour a test can observe, so this asserts the one thing the
 * deletion changes: the class is no longer in the build. The second test is the positive control
 * for the same instrument -- a lookup that cannot find anything would pass the first test for the
 * wrong reason.
 *
 * @author wisdomme
 * @version 2.0.0
 */
@DisplayName("WorldListGUI removal (UltiWorlds#18)")
class WorldListGuiRemovalTest {

    private static final String REMOVED_CLASS = "com.ultikits.plugins.worlds.gui.WorldListGUI";

    @Test
    @DisplayName("the orphaned WorldListGUI class is not shipped")
    void theOrphanedWorldListGuiIsNotShipped() {
        assertThatThrownBy(() -> Class.forName(REMOVED_CLASS, false, getClass().getClassLoader()))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    @DisplayName("control: the same lookup finds the live WorldListPage")
    void theSameLookupFindsTheLiveWorldListPage() throws ClassNotFoundException {
        assertThat(Class.forName("com.ultikits.plugins.worlds.gui.WorldListPage", false,
                getClass().getClassLoader()))
                .isSameAs(WorldListPage.class);
    }
}
