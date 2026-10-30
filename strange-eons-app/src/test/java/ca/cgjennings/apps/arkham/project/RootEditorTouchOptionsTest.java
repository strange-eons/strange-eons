package ca.cgjennings.apps.arkham.project;

import ca.cgjennings.apps.arkham.CommandLineArguments;
import ca.cgjennings.apps.arkham.StrangeEons;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JCheckBox;
import javax.swing.JRadioButton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import resources.Settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class RootEditorTouchOptionsTest {

    private static final String AUTOTOUCH = MakeBundle.KEY_AUTOTOUCH;
    private static final String WRITE = MakeBundle.KEY_WRITE_TOUCHED_ROOT;

    @TempDir
    Path tmp;

    private String oldAutotouch;
    private String oldWrite;
    private RootEditor editor;

    @BeforeEach
    void saveSettings() {
        assumeFalse(GraphicsEnvironment.isHeadless());
        oldAutotouch = Settings.getUser().get(AUTOTOUCH);
        oldWrite = Settings.getUser().get(WRITE);
    }

    @AfterEach
    void restoreSettings() {
        if (editor != null) {
            editor.dispose();
        }
        restore(AUTOTOUCH, oldAutotouch);
        restore(WRITE, oldWrite);
    }

    private static void restore(String key, String value) {
        if (value == null) {
            Settings.getUser().reset(key);
        } else {
            Settings.getUser().set(key, value);
        }
    }

    private RootEditor open(String autotouch, String write) throws Exception {
        Settings.getUser().set(AUTOTOUCH, autotouch);
        Settings.getUser().set(WRITE, write);
        File rootFile = tmp.resolve("eons-plugin").toFile();
        Files.writeString(rootFile.toPath(), "");
        Member root = mock(Member.class, RETURNS_DEEP_STUBS);
        when(root.getFile()).thenReturn(rootFile);
        when(root.getProject().getView()).thenReturn(null);
        Constructor<RootEditor> c = RootEditor.class.getDeclaredConstructor(Member.class);
        c.setAccessible(true);
        StrangeEons application = mock(StrangeEons.class);
        when(application.getCommandLineArguments()).thenReturn(mock(CommandLineArguments.class));
        try (MockedStatic<StrangeEons> app = mockStatic(StrangeEons.class, CALLS_REAL_METHODS)) {
            app.when(StrangeEons::getApplication).thenReturn(application);
            app.when(StrangeEons::getWindow).thenReturn(null);
            editor = c.newInstance(root);
        }
        return editor;
    }

    private static <T> T field(RootEditor e, String name, Class<T> type) throws Exception {
        Field f = RootEditor.class.getDeclaredField(name);
        f.setAccessible(true);
        return type.cast(f.get(e));
    }

    private static JRadioButton project(RootEditor e) throws Exception {
        return field(e, "touchProjectRadio", JRadioButton.class);
    }

    private static JRadioButton bundle(RootEditor e) throws Exception {
        return field(e, "touchBundleRadio", JRadioButton.class);
    }

    @Test
    void radiosReflectSetting() throws Exception {
        RootEditor e = open("yes", "yes");
        assertTrue(project(e).isSelected());
        assertFalse(bundle(e).isSelected());
        e.dispose();

        e = open("yes", "no");
        assertFalse(project(e).isSelected());
        assertTrue(bundle(e).isSelected());
    }

    @Test
    void radiosUpdateSetting() throws Exception {
        RootEditor e = open("yes", "yes");

        bundle(e).doClick();
        assertEquals("no", Settings.getUser().get(WRITE));
        assertFalse(project(e).isSelected());

        project(e).doClick();
        assertEquals("yes", Settings.getUser().get(WRITE));
    }

    @Test
    void radiosFollowAutotouchCheck() throws Exception {
        RootEditor e = open("yes", "yes");
        assertTrue(project(e).isEnabled());
        assertTrue(bundle(e).isEnabled());

        JCheckBox check = field(e, "autotouchCheck", JCheckBox.class);
        check.doClick();
        assertFalse(project(e).isEnabled());
        assertFalse(bundle(e).isEnabled());

        check.doClick();
        assertTrue(project(e).isEnabled());
        assertTrue(bundle(e).isEnabled());
    }

    @Test
    void radiosStartDisabledWhenAutotouchIsOff() throws Exception {
        RootEditor e = open("no", "yes");

        assertFalse(project(e).isEnabled());
        assertFalse(bundle(e).isEnabled());
    }
}
