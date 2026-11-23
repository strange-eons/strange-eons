package resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.cgjennings.io.protocols.project.Handler;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StrangeImageTest {

    @TempDir
    Path tmp;

    private Path write(Path file, Color color, long modified) throws IOException {
        Files.createDirectories(file.getParent());
        BufferedImage bi = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        bi.setRGB(0, 0, color.getRGB());
        ImageIO.write(bi, "png", file.toFile());
        assertTrue(file.toFile().setLastModified(modified));
        return file;
    }

    private static int pixel(String identifier) {
        return StrangeImage.getAsBufferedImage(identifier).getRGB(0, 0);
    }

    private static URL project(String spec) throws IOException {
        return new URL(null, spec, new Handler());
    }

    @Test
    void reloadsModifiedPlainPath() throws IOException {
        Path file = write(tmp.resolve("plain.png"), Color.RED, 1_000_000L);
        String id = file.toString();
        assertEquals(Color.RED.getRGB(), pixel(id));
        write(file, Color.BLUE, 2_000_000L);
        assertEquals(Color.BLUE.getRGB(), pixel(id));
    }

    @Test
    void reloadsModifiedFileUrl() throws IOException {
        Path file = write(tmp.resolve("url.png"), Color.RED, 1_000_000L);
        String id = file.toUri().toString();
        assertEquals(Color.RED.getRGB(), pixel(id));
        write(file, Color.BLUE, 2_000_000L);
        assertEquals(Color.BLUE.getRGB(), pixel(id));
    }

    @Test
    void unchangedFileIsCached() throws IOException {
        Path file = write(tmp.resolve("same.png"), Color.RED, 1_000_000L);
        StrangeImage first = StrangeImage.get(file.toString());
        assertSame(first, StrangeImage.get(file.toString()));
        assertSame(first, StrangeImage.get(file.toUri().toString()));
    }

    @Test
    void projectImageWithoutProjectIsMissing() {
        assertSame(StrangeImage.getMissingImage(), StrangeImage.get("project:none.png"));
    }

    @Test
    void resolvesProjectUrlsInFolder() throws IOException {
        Path folder = Files.createDirectories(tmp.resolve("spaced project"));
        File expected = folder.resolve("art/my art+1.png").toFile();

        assertEquals(expected, StrangeImage.fileFromURL(Handler.resolve(project("project:art/my art+1.png"), folder.toFile())));
        assertEquals(expected, StrangeImage.fileFromURL(Handler.resolve(project("project://art/my%20art+1.png"), folder.toFile())));
        assertEquals(expected, StrangeImage.fileFromURL(Handler.resolve(project("project:///art/my art+1.png"), folder.toFile())));
        assertNull(Handler.resolve(project("project:a.png"), null));
    }

    @Test
    void localFileResolvesIdentifiers() throws IOException {
        File plain = tmp.resolve("a b.png").toFile();
        assertEquals(plain, local(plain.getPath()));
        assertEquals(plain, local(plain.toURI().toString()));
        assertEquals(tmp.resolve("50%.png").toFile(), local("file:" + tmp + "/50%.png"));
        assertNull(local("http://example.com/a.png"));
        assertNull(StrangeImage.localFile("project:a.png", project("project:a.png")));
        assertNull(StrangeImage.fileFromURL(null));
    }

    private static File local(String identifier) {
        return StrangeImage.localFile(identifier, StrangeImage.identifierToURL(identifier));
    }
}
