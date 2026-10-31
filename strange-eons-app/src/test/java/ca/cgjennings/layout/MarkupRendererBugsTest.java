package ca.cgjennings.layout;

import java.awt.Graphics2D;
import java.awt.font.GlyphVector;
import java.awt.font.TextAttribute;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Tests asserting what {@link MarkupRenderer} and its collaborators
 * <em>should</em> do. Those that fail today are {@link Disabled}, with
 * the observed behavior as the reason; remove the annotation when the
 * behavior changes. Disputed tests may not describe defects, and exist
 * to record the question.
 */
class MarkupRendererBugsTest {

    // ---- Markup must not hang or crash rendering --------------------

    @Test
    void zeroTabWidthShouldNotThrowAtTab() {
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText("<tabwidth garbage>a\tb");
        try (GraphicsHandle gh = new GraphicsHandle(400, 200)) {
            assertDoesNotThrow(() -> r.draw(gh.g, new Rectangle2D.Double(0, 0, 300, 100)));
        }
    }

    @Test
    void allZeroTabWidthsShouldNotHangLayout() throws InterruptedException {
        assertLayoutFinishes("<tabwidth garbage garbage>a");
    }

    @Test
    void selfReferencingDefinitionShouldNotHangLayout() throws InterruptedException {
        assertLayoutFinishes("<define loop \"<loop>\"><loop>");
    }

    @Test
    void exceedingExpansionLimitShouldNotThrow() {
        MarkupRenderer r = new MarkupRenderer();
        r.setExpansionLimit(100);
        r.setMarkupText("<define grow \"x<grow>\"><grow>");
        try (GraphicsHandle gh = new GraphicsHandle(400, 200)) {
            assertDoesNotThrow(() -> r.draw(gh.g, new Rectangle2D.Double(0, 0, 300, 100)));
        }
    }

    // ---- Incorrect results ------------------------------------------

    @Test
    @Disabled("BUG: measure(\"\") returns ~9.97 (about one line of "
            + "default font), not 0. The renderer emits one padding-"
            + "character segment for baseline anchoring; that segment's "
            + "height leaks into measure(). Callers asking 'how tall "
            + "is this layout?' for empty content get a one-line "
            + "answer. Fix: either return 0 for empty input, or "
            + "document that measure() includes the minimum baseline "
            + "padding and provide a separate height() for content-only.")
    void measureEmptyMarkupShouldReturnZeroOrNegligibleHeight() {
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText("");
        try (GraphicsHandle gh = new GraphicsHandle(200, 200)) {
            double h = r.measure(gh.g, new Rectangle2D.Double(0, 0, 100, 100));
            assertTrue(h < 1.0,
                    "expected empty markup to measure as ~0 height; got " + h);
        }
    }

    @Test
    @Disabled("BUG: <define name a b c> uses only the first content "
            + "parameter ('a'); 'b c' are silently dropped. A user "
            + "expecting <define greeting Hello world> to set greeting "
            + "= 'Hello world' gets 'Hello' only. Fix: either join "
            + "parameters with a space, or document that multi-word "
            + "definitions need quoting.")
    void defineWithExtraParametersShouldJoinOrError() {
        int withGreeting = totalGlyphsRendered(
                "<define greeting Hello world>The <greeting>.");
        int withHelloOnly = totalGlyphsRendered("The Hello.");
        assertTrue(withGreeting > withHelloOnly,
                "<define name a b c> should expand to 'a b c' not just 'a'; "
                        + "withGreeting=" + withGreeting
                        + " withHelloOnly=" + withHelloOnly);
    }

    @Test
    @Disabled("BUG: without fractional metrics, advances round to whole units "
            + "at small scaled sizes and the single-pass scale can overshoot. "
            + "The result varies by platform font: 219 in this 200-wide region "
            + "on macOS and Linux, 139 in a 100-wide region on Windows.")
    void drawAsSingleLineShouldNotOverflowRegion() {
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText("Hello, world. ".repeat(10));
        try (GraphicsHandle gh = new GraphicsHandle(400, 200)) {
            double width = r.drawAsSingleLine(gh.g, new Rectangle2D.Double(0, 0, 200, 30));
            assertTrue(width <= 200d, "width " + width);
        }
    }

    @Test
    void drawAsSingleLineShouldNotAffectLaterLayout() {
        MarkupRenderer r = new MarkupRenderer();
        r.setTextFitting(MarkupRenderer.FIT_SCALE_TEXT);
        r.setMarkupText("Line ".repeat(60));
        Rectangle2D region = new Rectangle2D.Double(0, 0, 100, 40);
        try (GraphicsHandle gh = new GraphicsHandle(400, 200)) {
            double before = r.measure(gh.g, region);
            r.drawAsSingleLine(gh.g, region);
            assertEquals(before, r.measure(gh.g, region), 0.01d);
        }
    }

    @Test
    void resizingSameRectangleObjectShouldRepeatFitting() {
        MarkupRenderer r = new MarkupRenderer();
        r.setTextFitting(MarkupRenderer.FIT_SCALE_TEXT);
        r.setMarkupText("Line ".repeat(60));
        Rectangle2D.Double region = new Rectangle2D.Double(0, 0, 100, 40);
        try (GraphicsHandle gh = new GraphicsHandle(600, 400)) {
            r.measure(gh.g, region);
            region.setRect(0, 0, 500, 300);
            double reused = r.measure(gh.g, region);

            MarkupRenderer fresh = new MarkupRenderer();
            fresh.setTextFitting(MarkupRenderer.FIT_SCALE_TEXT);
            fresh.setMarkupText("Line ".repeat(60));
            assertEquals(fresh.measure(gh.g, new Rectangle2D.Double(0, 0, 500, 300)), reused, 0.01d);
        }
    }

    @Test
    @Disabled("BUG: draw is documented to return where the next line would "
            + "start, but with FIT_SCALE_TEXT and text without whitespace it "
            + "returns lastLaidOutYOffset: 0 instead of about 10.35.")
    void drawOfSingleWordShouldReturnNextLinePosition() {
        Rectangle2D region = new Rectangle2D.Double(0, 0, 100, 100);
        MarkupRenderer scaled = new MarkupRenderer();
        scaled.setTextFitting(MarkupRenderer.FIT_SCALE_TEXT);
        scaled.setMarkupText("Word");
        MarkupRenderer plain = new MarkupRenderer();
        plain.setMarkupText("Word");
        try (GraphicsHandle gh = new GraphicsHandle(400, 200)) {
            assertEquals(plain.draw(gh.g, region), scaled.draw(gh.g, region), 0.01d);
        }
    }

    @Test
    void firstMeasureShouldMatchTightenedLayout() {
        MarkupRenderer unfitted = new MarkupRenderer();
        unfitted.setMarkupText("Line ".repeat(60));
        MarkupRenderer tightest = new MarkupRenderer();
        tightest.setLineTightness(0.5f);
        tightest.setMarkupText("Line ".repeat(60));
        MarkupRenderer r = new MarkupRenderer();
        r.setTextFitting(MarkupRenderer.FIT_TIGHTEN_LINE_SPACING);
        r.setMarkupText("Line ".repeat(60));
        try (GraphicsHandle gh = new GraphicsHandle(400, 500)) {
            Rectangle2D wide = new Rectangle2D.Double(0, 0, 300, 400);
            double h = (unfitted.measure(gh.g, wide) + tightest.measure(gh.g, wide)) / 2d;
            Rectangle2D region = new Rectangle2D.Double(0, 0, 300, h);
            double first = r.measure(gh.g, region);
            assertEquals(r.measure(gh.g, region), first, 0.01d);
        }
    }

    // ---- Disputed: preferences and smells ---------------------------

    @Test
    @Disabled("DISPUTED: WeightStyleFactory defaults to WIDTH_REGULAR rather than "
            + "WEIGHT_REGULAR. Both are 1.0f, so this has no effect; assertSame "
            + "fails because the references differ. The class comment and "
            + "standardWidth* fields carry the same copy-paste.")
    void weightFactoryDefaultShouldBeWeightRegularNotWidthRegular() {
        TextStyle style = WeightStyleFactory.getShared()
                .createStyle(null, new String[] {});
        Float value = (Float) style.get(TextAttribute.WEIGHT);
        assertSame(TextAttribute.WEIGHT_REGULAR, value,
                "expected WEIGHT_REGULAR as default; got the WIDTH_REGULAR constant");
    }

    @Test
    @Disabled("DISPUTED: WeightStyleFactory accepts numeric values up to 100. Java "
            + "does not bound TextAttribute.WEIGHT, so this is a validation "
            + "preference.")
    void weightFactoryShouldRejectOutOfRangeValues() {
        TextStyle style = WeightStyleFactory.getShared()
                .createStyle(null, new String[] {"50"});
        assertEquals(TextAttribute.WEIGHT_REGULAR, style.get(TextAttribute.WEIGHT),
                "expected weight=50 (way out of range) to fall back to WEIGHT_REGULAR");
    }

    @Test
    @Disabled("DISPUTED: WidthStyleFactory accepts numeric values up to 100. Java "
            + "does not bound TextAttribute.WIDTH, so this is a validation "
            + "preference.")
    void widthFactoryShouldRejectOutOfRangeValues() {
        TextStyle style = WidthStyleFactory.getShared()
                .createStyle(null, new String[] {"50"});
        assertEquals(TextAttribute.WIDTH_REGULAR, style.get(TextAttribute.WIDTH),
                "expected width=50 (way out of range) to fall back to WIDTH_REGULAR");
    }

    @Test
    @Disabled("DISPUTED: setLineTightness accepts negative values, but renderText "
            + "branches on getLineTightness() >= 0, so they may be intentional.")
    void setNegativeLineTightnessShouldThrowOrClipToPositive() {
        MarkupRenderer r = new MarkupRenderer();
        r.setLineTightness(-1.0f);
        assertTrue(r.getLineTightness() > 0,
                "expected line tightness to be clipped to positive; got "
                        + r.getLineTightness());
    }

    @Test
    @Disabled("DISPUTED: <lt> is hard-coded in the parser and renders correctly, "
            + "but getReplacementForTag(\"lt\") returns null.")
    void ltShouldBeRegisteredAsAReplacement() {
        MarkupRenderer r = new MarkupRenderer();
        assertNotNull(r.getReplacementForTag("lt"),
                "expected <lt> to appear in the replacement table for introspection");
    }

    @Test
    @Disabled("DISPUTED: <gt> is hard-coded in the parser and renders correctly, "
            + "but getReplacementForTag(\"gt\") returns null.")
    void gtShouldBeRegisteredAsAReplacement() {
        MarkupRenderer r = new MarkupRenderer();
        assertNotNull(r.getReplacementForTag("gt"),
                "expected <gt> to appear in the replacement table for introspection");
    }

    // ---- Unverified -------------------------------------------------

    @Test
    @Disabled("UNVERIFIED: TextLayout applies SUPERSCRIPT inside the glyph vector, so "
            + "the y passed to drawGlyphVector does not move. This test cannot "
            + "observe the offset; it needs visual bounds or pixel comparison.")
    void supTagShouldRaiseBaselineAboveSurroundingText() {
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText("x<sup>2</sup>x");

        Graphics2D spyG = spy(newImage(400, 200).createGraphics());
        try {
            r.draw(spyG, new Rectangle2D.Double(0, 0, 300, 100));
            ArgumentCaptor<Float> yCaptor = ArgumentCaptor.forClass(Float.class);
            verify(spyG, atLeast(2)).drawGlyphVector(
                    org.mockito.ArgumentMatchers.any(GlyphVector.class),
                    anyFloat(),
                    yCaptor.capture());
            float maxY = yCaptor.getAllValues().stream().reduce(Float.NEGATIVE_INFINITY, Float::max);
            float minY = yCaptor.getAllValues().stream().reduce(Float.POSITIVE_INFINITY, Float::min);
            assertTrue(maxY - minY > 0.5f,
                    "expected <sup> to produce a y-offset difference; "
                            + "ys=" + yCaptor.getAllValues());
        } finally {
            spyG.dispose();
        }
    }

    // ---- Suspicions the tests refuted ---------------------------------

    @Test
    void fitScaleTextDoesScaleSingleLongWord() {
        // Text without whitespace is sent to drawAsSingleLine, which
        // scales it even though it cannot wrap.
        String singleWord = "<size 36>extraordinarilylongword</size>";
        double extentFitNone = totalVisualWidth(singleWord,
                MarkupRenderer.FIT_NONE, 80, 30);
        double extentFitScale = totalVisualWidth(singleWord,
                MarkupRenderer.FIT_SCALE_TEXT, 80, 30);
        assertTrue(extentFitScale < extentFitNone,
                "FIT_SCALE_TEXT scales single oversized words; "
                        + "fitNone=" + extentFitNone + " fitScale=" + extentFitScale);
    }

    // ---- Helpers ----------------------------------------------------

    private static void assertLayoutFinishes(String markup) throws InterruptedException {
        // A daemon thread, since a spinning loop ignores interruption.
        Thread layout = new Thread(() -> {
            MarkupRenderer r = new MarkupRenderer();
            r.setMarkupText(markup);
            try (GraphicsHandle gh = new GraphicsHandle(400, 200)) {
                r.draw(gh.g, new Rectangle2D.Double(0, 0, 300, 100));
            }
        });
        layout.setDaemon(true);
        layout.start();
        layout.join(5000);
        assertFalse(layout.isAlive(), "layout did not finish");
    }

    private static int totalGlyphsRendered(String markup) {
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText(markup);
        Graphics2D spyG = spy(newImage(400, 200).createGraphics());
        try {
            r.draw(spyG, new Rectangle2D.Double(0, 0, 300, 100));
            ArgumentCaptor<GlyphVector> gv = ArgumentCaptor.forClass(GlyphVector.class);
            verify(spyG, atLeast(0)).drawGlyphVector(gv.capture(), anyFloat(), anyFloat());
            return gv.getAllValues().stream()
                    .mapToInt(GlyphVector::getNumGlyphs)
                    .sum();
        } finally {
            spyG.dispose();
        }
    }

    private static double totalVisualWidth(String markup, int fitMode,
            double w, double h) {
        MarkupRenderer r = new MarkupRenderer();
        r.setTextFitting(fitMode);
        r.setMarkupText(markup);
        Graphics2D spyG = spy(newImage((int) w + 200, (int) h + 200).createGraphics());
        try {
            r.draw(spyG, new Rectangle2D.Double(0, 0, w, h));
            ArgumentCaptor<GlyphVector> gv = ArgumentCaptor.forClass(GlyphVector.class);
            verify(spyG, atLeast(0)).drawGlyphVector(gv.capture(), anyFloat(), anyFloat());
            return gv.getAllValues().stream()
                    .mapToDouble(v -> v.getVisualBounds().getWidth())
                    .sum();
        } finally {
            spyG.dispose();
        }
    }

    private static BufferedImage newImage(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    private static final class GraphicsHandle implements AutoCloseable {
        final BufferedImage img;
        final Graphics2D g;

        GraphicsHandle(int w, int h) {
            this.img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            this.g = img.createGraphics();
        }

        @Override
        public void close() {
            g.dispose();
        }
    }
}
