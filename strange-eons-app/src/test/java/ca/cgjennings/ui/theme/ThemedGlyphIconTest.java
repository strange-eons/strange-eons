package ca.cgjennings.ui.theme;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;

class ThemedGlyphIconTest {

    @Test
    void paintsWithoutDesktopFontHints() {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Toolkit toolkit = spy(Toolkit.getDefaultToolkit());
        doReturn(null).when(toolkit).getDesktopProperty("awt.font.desktophints");
        ThemedGlyphIcon icon = new ThemedGlyphIcon("A", Color.BLACK, Color.WHITE);
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try (MockedStatic<Toolkit> tk = mockStatic(Toolkit.class, CALLS_REAL_METHODS)) {
            tk.when(Toolkit::getDefaultToolkit).thenReturn(toolkit);
            assertDoesNotThrow(() -> icon.paintIcon(null, g, 0, 0));
        } finally {
            g.dispose();
        }
    }
}
