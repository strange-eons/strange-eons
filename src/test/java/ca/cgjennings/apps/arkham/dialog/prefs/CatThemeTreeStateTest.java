package ca.cgjennings.apps.arkham.dialog.prefs;

import ca.cgjennings.apps.arkham.plugins.BundleInstaller;
import ca.cgjennings.apps.arkham.plugins.InstalledTheme;
import ca.cgjennings.apps.arkham.project.ProjectView;
import ca.cgjennings.ui.theme.Theme;
import ca.cgjennings.ui.theme.ThemeInstaller;
import java.awt.GraphicsEnvironment;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import resources.Settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class CatThemeTreeStateTest {

    private static final String RECENT_KEY = "recent-file-menu-length";
    private static final String TREE_STATE_KEY = ProjectView.KEY_STORE_TREE_STATE;

    @Test
    void storeTreeStateCheckRoundTrips() {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Settings user = Settings.getUser();
        String old = user.get(TREE_STATE_KEY);
        String oldRecent = user.get(RECENT_KEY);
        try (MockedStatic<BundleInstaller> bi = mockStatic(BundleInstaller.class);
                MockedStatic<ThemeInstaller> ti = mockStatic(ThemeInstaller.class)) {
            bi.when(BundleInstaller::getInstalledThemes).thenReturn(new InstalledTheme[0]);
            ti.when(ThemeInstaller::getInstalledTheme).thenReturn(mock(Theme.class));
            CatTheme cat = new CatTheme();

            user.set(RECENT_KEY, "5");
            user.set(TREE_STATE_KEY, "yes");
            cat.loadSettings();
            assertTrue(cat.storeTreeStateCheck.isSelected());
            cat.storeTreeStateCheck.setSelected(false);
            cat.storeSettings();
            assertEquals("no", user.get(TREE_STATE_KEY));
        } finally {
            restore(user, TREE_STATE_KEY, old);
            restore(user, RECENT_KEY, oldRecent);
        }
    }

    private static void restore(Settings s, String key, String value) {
        if (value == null) {
            s.reset(key);
        } else {
            s.set(key, value);
        }
    }
}
