package ca.cgjennings.apps.arkham.project;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import resources.Settings;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ProjectViewTreeStateTest {

    @TempDir
    Path tmp;

    @Test
    void clearRemovesOnlyTreeState() {
        Settings s = new Settings();
        s.set("open-folders", "a");
        s.set("selected-members", "b");
        s.set("first-row", "1");
        s.set("notes", "keep");

        ProjectView.clearTreeState(s);

        assertNull(s.get("open-folders"));
        assertNull(s.get("selected-members"));
        assertNull(s.get("first-row"));
        assertEquals("keep", s.get("notes"));
    }

    @Test
    void clearWithoutTreeStateDoesNotDirtySettings() {
        Settings s = new Settings();
        s.set("notes", "keep");
        AtomicInteger changes = new AtomicInteger();
        s.addPropertyChangeListener(e -> changes.incrementAndGet());

        ProjectView.clearTreeState(s);

        assertEquals(0, changes.get());
    }

    private static Settings sampleState() {
        Settings state = new Settings();
        state.set("open-folders", ":a b/c:d");
        state.set("selected-members", ":a b");
        state.set("first-row", "3");
        return state;
    }

    private File stateFile() {
        return tmp.resolve(ProjectView.LOCAL_STATE_FOLDER).resolve(ProjectView.TREE_STATE_FILE).toFile();
    }

    @Test
    void saveToFileMovesStateOutOfSettings() throws IOException {
        Settings s = sampleState();
        s.set("notes", "keep");

        ProjectView.saveTreeState(tmp.toFile(), s, sampleState(), false);

        assertNull(s.get("open-folders"));
        assertEquals("keep", s.get("notes"));
        Settings loaded = ProjectView.loadTreeState(tmp.toFile(), s);
        assertEquals(":a b/c:d", loaded.get("open-folders"));
        assertEquals(":a b", loaded.get("selected-members"));
        assertEquals(3, loaded.getInt("first-row", 0));
    }

    @Test
    void saveToFileDoesNotDirtySettingsWithoutOldState() throws IOException {
        Settings s = new Settings();
        AtomicInteger changes = new AtomicInteger();
        s.addPropertyChangeListener(e -> changes.incrementAndGet());

        ProjectView.saveTreeState(tmp.toFile(), s, sampleState(), false);
        ProjectView.saveTreeState(tmp.toFile(), s, sampleState(), false);

        assertEquals(0, changes.get());
        assertTrue(stateFile().isFile());
    }

    @Test
    void saveToFileSkipsMissingProjectFolder() throws IOException {
        File missing = tmp.resolve("deleted").toFile();
        Settings s = sampleState();

        ProjectView.saveTreeState(missing, s, sampleState(), false);

        assertFalse(missing.exists());
        assertEquals(":a b", s.get("selected-members"));
    }

    @Test
    void failedSaveKeepsStateInSettings() throws IOException {
        Files.writeString(tmp.resolve(ProjectView.LOCAL_STATE_FOLDER), "not a folder");
        Settings s = sampleState();

        assertThrows(IOException.class, () -> ProjectView.saveTreeState(tmp.toFile(), s, sampleState(), false));

        assertEquals(":a b", s.get("selected-members"));
    }

    @Test
    void saveToSettingsRemovesFileAndEmptyFolder() throws IOException {
        Settings s = new Settings();
        ProjectView.saveTreeState(tmp.toFile(), s, sampleState(), false);

        ProjectView.saveTreeState(tmp.toFile(), s, sampleState(), true);

        assertEquals(":a b", s.get("selected-members"));
        assertFalse(stateFile().getParentFile().exists());
    }

    @Test
    void saveToSettingsKeepsFolderWithOtherContent() throws IOException {
        Settings s = new Settings();
        ProjectView.saveTreeState(tmp.toFile(), s, sampleState(), false);
        Path other = stateFile().getParentFile().toPath().resolve("other");
        Files.writeString(other, "x");

        ProjectView.saveTreeState(tmp.toFile(), s, sampleState(), true);

        assertFalse(stateFile().exists());
        assertTrue(Files.exists(other));
    }

    @Test
    void saveToSettingsWithoutFileLeavesNoFolder() throws IOException {
        ProjectView.saveTreeState(tmp.toFile(), new Settings(), sampleState(), true);

        assertFalse(stateFile().getParentFile().exists());
    }

    @Test
    void loadFallsBackToSettingsWithoutFile() {
        Settings s = sampleState();

        assertSame(s, ProjectView.loadTreeState(tmp.toFile(), s));
    }

    @Test
    void loadFallsBackToSettingsWhenStateFileIsFolder() {
        assertTrue(stateFile().mkdirs());
        Settings s = sampleState();

        assertSame(s, ProjectView.loadTreeState(tmp.toFile(), s));
    }

    @Test
    void loadFallsBackToSettingsWhenFileIsUnreadable() throws IOException {
        ProjectView.saveTreeState(tmp.toFile(), new Settings(), sampleState(), false);
        assumeTrue(stateFile().setReadable(false) && !stateFile().canRead());
        Settings s = sampleState();
        try {
            assertSame(s, ProjectView.loadTreeState(tmp.toFile(), s));
        } finally {
            stateFile().setReadable(true);
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void stateFolderIsHiddenOnWindows() throws IOException {
        Settings s = new Settings();
        ProjectView.saveTreeState(tmp.toFile(), s, sampleState(), false);
        ProjectView.saveTreeState(tmp.toFile(), s, sampleState(), false);

        assertTrue(stateFile().getParentFile().isHidden());
        assertFalse(stateFile().isHidden());
    }

    @Test
    void setDosHiddenToleratesFailure() {
        File missing = tmp.resolve("missing").toFile();

        assertDoesNotThrow(() -> ProjectView.setDosHidden(missing));
        assertFalse(missing.exists());
    }

    @Test
    void defaultsKeepExistingBehavior() throws IOException {
        Properties defaults = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/resources/default.settings")) {
            defaults.load(in);
        }
        assertEquals("yes", defaults.getProperty(ProjectView.KEY_STORE_TREE_STATE));
        assertEquals("yes", defaults.getProperty(MakeBundle.KEY_WRITE_TOUCHED_ROOT));
    }
}
