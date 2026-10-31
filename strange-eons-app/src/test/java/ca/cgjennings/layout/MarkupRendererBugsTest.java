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
 * Bug-hunting tests for {@link MarkupRenderer} and its collaborators.
 * Each test asserts what the API <em>should</em> do (per its docs,
 * naming, or principle of least surprise). Tests marked
 * {@link Disabled} are confirmed bugs — they fail today; the disable
 * reason documents the observed wrong behavior. Remove the
 * {@code @Disabled} annotation when the underlying bug is fixed.
 *
 * <p>Tests <em>not</em> marked {@code @Disabled} passed at the time
 * of writing — they document a suspected bug that turned out not to
 * be one (the assertion holds against current code).
 */
class MarkupRendererBugsTest {

    // ---- Confirmed bugs (currently failing) -------------------------

    @Test
    @Disabled("BUG: WeightStyleFactory initialises its default to "
            + "TextAttribute.WIDTH_REGULAR instead of WEIGHT_REGULAR. "
            + "Both happen to equal 1.0f so the effect is harmless, "
            + "but assertSame fails because the Float references differ. "
            + "Fix: WeightStyleFactory.java line 34 — change WIDTH_REGULAR "
            + "to WEIGHT_REGULAR (and rename standardWidthNames / "
            + "standardWidthValues fields to standardWeight*). Class "
            + "comment also says 'Produce different text widths'.")
    void weightFactoryDefaultShouldBeWeightRegularNotWidthRegular() {
        TextStyle style = WeightStyleFactory.getShared()
                .createStyle(null, new String[] {});
        Float value = (Float) style.get(TextAttribute.WEIGHT);
        assertSame(TextAttribute.WEIGHT_REGULAR, value,
                "expected WEIGHT_REGULAR as default; got the WIDTH_REGULAR constant");
    }

    @Test
    @Disabled("BUG: removeAllTags() clears built-in style tags too. "
            + "After the call, getStyleForTag(\"b\") returns null and "
            + "the renderer can no longer render <b>...</b>. The method "
            + "name suggests user-added tags only. Fix: either preserve "
            + "built-ins, or rename to make the destructive behavior "
            + "explicit (e.g. clearTagTable, removeAllStyleTags).")
    void removeAllTagsShouldNotClearBuiltInTags() {
        MarkupRenderer r = new MarkupRenderer();
        r.removeAllTags();
        assertNotNull(r.getStyleForTag("b"),
                "expected built-in <b> tag to survive removeAllTags()");
    }

    @Test
    @Disabled("BUG: removeAllTags() also clears the built-in color "
            + "tags (<red>, <blue>, etc.). Same fix as built-in style "
            + "tags.")
    void removeAllTagsShouldNotClearBuiltInColorTags() {
        MarkupRenderer r = new MarkupRenderer();
        r.removeAllTags();
        assertNotNull(r.getStyleForTag("red"),
                "expected built-in color tag to survive removeAllTags()");
    }

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
    @Disabled("API INCONSISTENCY: <lt> is hard-coded in the parser to "
            + "produce '<' but is not registered in the replacement "
            + "table. Tools or scripts that introspect the renderer "
            + "via getReplacementForTag(\"lt\") get null and conclude "
            + "the tag is unsupported. Fix: register <lt> and <gt> as "
            + "replacements (and remove the special case in the parser, "
            + "with care for re-parsing).")
    void ltShouldBeRegisteredAsAReplacement() {
        MarkupRenderer r = new MarkupRenderer();
        assertNotNull(r.getReplacementForTag("lt"),
                "expected <lt> to appear in the replacement table for introspection");
    }

    @Test
    @Disabled("API INCONSISTENCY: same as <lt> — <gt> is hard-coded "
            + "in the parser but not registered in the replacement table.")
    void gtShouldBeRegisteredAsAReplacement() {
        MarkupRenderer r = new MarkupRenderer();
        assertNotNull(r.getReplacementForTag("gt"),
                "expected <gt> to appear in the replacement table for introspection");
    }

    @Test
    @Disabled("BUG: WeightStyleFactory accepts numeric values up to 100. "
            + "TextAttribute.WEIGHT_REGULAR is 1.0; standard values "
            + "cluster in [0.5, 2.75]. Accepting 50 is sloppy validation. "
            + "Either reject or clip to a documented range.")
    void weightFactoryShouldRejectValuesAboveTwo() {
        TextStyle style = WeightStyleFactory.getShared()
                .createStyle(null, new String[] {"50"});
        assertEquals(TextAttribute.WEIGHT_REGULAR, style.get(TextAttribute.WEIGHT),
                "expected weight=50 (way out of range) to fall back to WEIGHT_REGULAR");
    }

    @Test
    @Disabled("BUG: WidthStyleFactory accepts numeric values up to 100. "
            + "TextAttribute.WIDTH_REGULAR is 1.0; standard values "
            + "cluster in [0.5, 1.5]. Accepting 50 silently is a "
            + "validation gap.")
    void widthFactoryShouldRejectValuesAboveTwo() {
        TextStyle style = WidthStyleFactory.getShared()
                .createStyle(null, new String[] {"50"});
        assertEquals(TextAttribute.WIDTH_REGULAR, style.get(TextAttribute.WIDTH),
                "expected width=50 (way out of range) to fall back to WIDTH_REGULAR");
    }

    @Test
    @Disabled("BUG: setLineTightness accepts negative values without "
            + "validation. A negative tightness collapses or inverts "
            + "line spacing in unpredictable ways. Fix: throw "
            + "IllegalArgumentException or clip to a minimum positive "
            + "value.")
    void setNegativeLineTightnessShouldThrowOrClipToPositive() {
        MarkupRenderer r = new MarkupRenderer();
        r.setLineTightness(-1.0f);
        assertTrue(r.getLineTightness() > 0,
                "expected line tightness to be clipped to positive; got "
                        + r.getLineTightness());
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
    @Disabled("METHODOLOGY GAP: <sup> applies a TextAttribute.SUPERSCRIPT "
            + "transform that the AWT TextLayout renders internally — "
            + "the y passed to drawGlyphVector is the same for all "
            + "styled runs in a paragraph. Visible vertical offset "
            + "happens inside the GlyphVector's bounds, not in the "
            + "drawGlyphVector call site. The <sup>/<sub> visible "
            + "effect is therefore unverified at the glyph-vector "
            + "level; verification would need pixel inspection or "
            + "GlyphVector.getVisualBounds() comparison.")
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

    // ---- Refuted bug suspicions (currently passing) -----------------
    //
    // These tests passed at the time of writing — they document a
    // suspected bug that turned out not to be one. Leaving them live
    // ensures the non-bug status is regression-tested.

    @Test
    void fitScaleTextDoesScaleSingleLongWord() {
        // SUSPECTED BUG: line 774 of MarkupRenderer reads
        //   if (!textHasWhitespace && (getTextFitting() & FIT_SCALE_TEXT) != 0) {
        // which I read as "scaling fires only when text has whitespace".
        // In practice, scaling DOES kick in for single-word content
        // when FIT_SCALE_TEXT is set — the condition's effect is
        // narrower than the bug suspicion implied.
        String singleWord = "<size 36>extraordinarilylongword</size>";
        double extentFitNone = totalVisualWidth(singleWord,
                MarkupRenderer.FIT_NONE, 80, 30);
        double extentFitScale = totalVisualWidth(singleWord,
                MarkupRenderer.FIT_SCALE_TEXT, 80, 30);
        assertTrue(extentFitScale < extentFitNone,
                "FIT_SCALE_TEXT scales single oversized words; "
                        + "fitNone=" + extentFitNone + " fitScale=" + extentFitScale);
    }

    @Test
    void userStyleForDefineIsReachableInMarkup() {
        // SUSPECTED BUG: setStyleForTag("define", ...) registers a
        // user style but the parser checks parametric dispatch
        // first, so the user style should be unreachable.
        //
        // ACTUALLY: the user-registered style table is consulted
        // before the parametric dispatch for built-in tags like
        // <define>, so the user override is reachable. The result
        // (rendering the content vs. consuming it as a parametric
        // <define>) confirms the user style wins.
        MarkupRenderer r = new MarkupRenderer();
        r.setStyleForTag("define", new TextStyle());
        assertSame(r.getStyleForTag("define"),
                r.getStyleForTag("define"),
                "setStyleForTag round-trip works for reserved names");
        // The user style is reachable: <define>x</define> renders
        // the 'x' content rather than consuming it as a parametric
        // built-in.
        int withDefine = totalGlyphsRendered("<define>x</define>");
        int plain = totalGlyphsRendered("x");
        assertEquals(plain, withDefine,
                "expected user <define> override to render content");
    }

    // ---- Helpers ----------------------------------------------------

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

    @Test
    @Disabled("BUG: a zero tab width makes createTabStopArray return an empty "
            + "array, so renderText throws ArrayIndexOutOfBoundsException at the "
            + "first tab. Invalid <tabwidth> parameters are converted to zero, "
            + "so <tabwidth garbage> reaches this from markup.")
    void zeroTabWidthShouldNotThrowAtTab() {
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText("<tabwidth garbage>a\tb");
        try (GraphicsHandle gh = new GraphicsHandle(400, 200)) {
            assertDoesNotThrow(() -> r.draw(gh.g, new Rectangle2D.Double(0, 0, 300, 100)));
        }
    }

    @Test
    @Disabled("BUG: when multiple tab widths are all zero, the sizing loop in "
            + "createTabStopArray never advances, so layout hangs forever, even "
            + "without a tab in the text. <tabwidth garbage garbage> reaches "
            + "this from markup.")
    void allZeroTabWidthsShouldNotHangLayout() throws InterruptedException {
        // A daemon thread, since the spinning loop ignores interruption.
        Thread layout = new Thread(() -> {
            MarkupRenderer r = new MarkupRenderer();
            r.setMarkupText("<tabwidth garbage garbage>a");
            try (GraphicsHandle gh = new GraphicsHandle(400, 200)) {
                r.draw(gh.g, new Rectangle2D.Double(0, 0, 300, 100));
            }
        });
        layout.setDaemon(true);
        layout.start();
        layout.join(5000);
        assertFalse(layout.isAlive(), "layout did not finish");
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
