package ca.cgjennings.apps.arkham.project;

import ca.cgjennings.apps.arkham.plugins.PluginRoot;
import ca.cgjennings.apps.arkham.plugins.catalog.CatalogID;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MakeBundleTest {

    private static final String ID = "CATALOGUEID{8b47a2f6-3d1c-4a7e-9f0b-2c6d1e5a7b90:2020-0-1-0-0-0-0}";

    @TempDir
    Path tmp;

    private static PluginRoot rootWithId() {
        return new PluginRoot("id = " + ID + "\n");
    }

    @Test
    void touchKeepsUuidAndUpdatesDate() {
        PluginRoot root = rootWithId();
        CatalogID before = root.getCatalogID();

        MakeBundle.touchRoot(root, false);

        assertEquals(before.getUUID(), root.getCatalogID().getUUID());
        assertNotEquals(before.toDateString(), root.getCatalogID().toDateString());
    }

    @Test
    void touchedIdIsWrittenOnlyWhenEnabled() {
        assertTrue(MakeBundle.touchRoot(rootWithId(), true));
        assertFalse(MakeBundle.touchRoot(rootWithId(), false));
    }

    @Test
    void generatedIdIsAlwaysWritten() {
        PluginRoot root = new PluginRoot("");

        assertTrue(MakeBundle.touchRoot(root, false));
        assertNotNull(root.getCatalogID());
    }

    @Test
    void packReplacesRootFileAndKeepsOtherFiles() throws IOException {
        File rootFile = file("eons-plugin", "id = " + ID + "\n");
        PluginRoot touched = new PluginRoot(rootFile);
        MakeBundle.touchRoot(touched, false);

        Map<String, String> entries = pack(touched, rootFile, file("data.txt", "data"));

        assertEquals(touched.toString(), entries.get("eons-plugin"));
        assertEquals("data", entries.get("data.txt"));
        assertEquals("id = " + ID + "\n", Files.readString(rootFile.toPath()));
    }

    @Test
    void packCopiesRootFileWithoutOverride() throws IOException {
        File rootFile = file("eons-plugin", "id = " + ID + "\n");

        Map<String, String> entries = pack(null, rootFile);

        assertEquals("id = " + ID + "\n", entries.get("eons-plugin"));
    }

    @Test
    void packOnlyReplacesRootFileAtTopLevel() throws IOException {
        File folder = tmp.resolve("sub").toFile();
        assertTrue(folder.mkdir());
        Path nested = folder.toPath().resolve("eons-plugin");
        Files.writeString(nested, "nested");

        Member sub = member(folder);
        Member nestedMember = member(nested.toFile());
        when(sub.isFolder()).thenReturn(true);
        when(sub.getChildren()).thenReturn(new Member[]{nestedMember});

        Member task = mock(Member.class);
        when(task.getChildren()).thenReturn(new Member[]{sub});

        Map<String, String> entries = pack(rootWithId(), task);

        assertEquals("nested", entries.get("sub/eons-plugin"));
        assertFalse(entries.containsKey("eons-plugin"));
    }

    private File file(String name, String content) throws IOException {
        Path p = tmp.resolve(name);
        Files.writeString(p, content);
        return p.toFile();
    }

    private static Member member(File f) {
        Member m = mock(Member.class);
        when(m.getFile()).thenReturn(f);
        when(m.getChildren()).thenReturn(new Member[0]);
        return m;
    }

    private static Map<String, String> pack(PluginRoot override, File... files) throws IOException {
        Member[] kids = new Member[files.length];
        for (int i = 0; i < files.length; ++i) {
            kids[i] = member(files[i]);
        }
        Member task = mock(Member.class);
        when(task.getChildren()).thenReturn(kids);
        return pack(override, task);
    }

    private static Map<String, String> pack(PluginRoot override, Member task) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            MakeBundle.pack(zip, task, "", override);
        }
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                entries.put(e.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}
