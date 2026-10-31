package ca.cgjennings.layout;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.TextAttribute;
import java.awt.font.TransformAttribute;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.text.AttributedCharacterIterator;
import java.text.CharacterIterator;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.withinPercentage;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Regression-safe coverage of {@link MarkupRenderer}'s public surface.
 * The class is old and largely untested; these tests pin the
 * existing behavior.
 *
 * <p>Tests are organized by concern: construction, getter/setter
 * round-trips, tag-table operations, and end-to-end draw / measure
 * against a {@link BufferedImage}-backed {@link Graphics2D}.
 */
class MarkupRendererTest {

    // ---- Construction ------------------------------------------------

    @Test
    void noArgConstructorUsesScreenDpi() {
        MarkupRenderer r = new MarkupRenderer();
        // The no-arg constructor uses 72 dpi; verify by checking that
        // default tags are populated (proves the constructor ran to
        // completion).
        assertNotNull(r.getStyleForTag("b"));
    }

    @Test
    void explicitDpiConstructor() {
        MarkupRenderer r = new MarkupRenderer(150d);
        assertNotNull(r.getStyleForTag("b"));
    }

    // ---- Markup text round-trip --------------------------------------

    @Test
    void getMarkupTextReturnsWhatWasSet() {
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText("Hello, world.");
        assertEquals("Hello, world.", r.getMarkupText());
    }

    @Test
    void getMarkupTextDefaultIsEmpty() {
        MarkupRenderer r = new MarkupRenderer();
        assertEquals("", r.getMarkupText());
    }

    // ---- Default style -----------------------------------------------

    @Test
    void defaultStyleIsPopulated() {
        MarkupRenderer r = new MarkupRenderer();
        assertNotNull(r.getDefaultStyle());
    }

    @Test
    void setDefaultStyleRoundTrips() {
        MarkupRenderer r = new MarkupRenderer();
        TextStyle custom = new TextStyle();
        r.setDefaultStyle(custom);
        assertSame(custom, r.getDefaultStyle());
    }

    // ---- Tag table ---------------------------------------------------

    @Test
    void builtInStyleTagsArePresent() {
        MarkupRenderer r = new MarkupRenderer();
        assertNotNull(r.getStyleForTag("b"));
        assertNotNull(r.getStyleForTag("i"));
        assertNotNull(r.getStyleForTag("u"));
        assertNotNull(r.getStyleForTag("sup"));
        assertNotNull(r.getStyleForTag("sub"));
        assertNotNull(r.getStyleForTag("tt"));
    }

    @Test
    void getStyleForUnknownTagReturnsNull() {
        MarkupRenderer r = new MarkupRenderer();
        assertNull(r.getStyleForTag("no-such-tag"));
    }

    @Test
    void setStyleForTagRoundTrips() {
        MarkupRenderer r = new MarkupRenderer();
        TextStyle style = new TextStyle();
        r.setStyleForTag("custom", style);
        assertSame(style, r.getStyleForTag("custom"));
    }

    @Test
    void removeTagRemovesStyle() {
        MarkupRenderer r = new MarkupRenderer();
        r.setStyleForTag("custom", new TextStyle());
        r.removeTag("custom");
        assertNull(r.getStyleForTag("custom"));
    }

    // ---- Replacement tags --------------------------------------------

    @Test
    void replacementTagRoundTrips() {
        MarkupRenderer r = new MarkupRenderer();
        r.setReplacementForTag("foo", "FooReplacement");
        assertEquals("FooReplacement", r.getReplacementForTag("foo"));
    }

    @Test
    void getReplacementForUnknownTagReturnsNull() {
        MarkupRenderer r = new MarkupRenderer();
        assertNull(r.getReplacementForTag("no-such-tag"));
    }

    // ---- Alignment ---------------------------------------------------

    @Test
    void defaultAlignmentIsLeftTop() {
        MarkupRenderer r = new MarkupRenderer();
        // Documented default: left + top
        assertEquals(MarkupRenderer.LAYOUT_LEFT | MarkupRenderer.LAYOUT_TOP,
                r.getAlignment());
    }

    @Test
    void setAlignmentRoundTrips() {
        MarkupRenderer r = new MarkupRenderer();
        int align = MarkupRenderer.LAYOUT_CENTER | MarkupRenderer.LAYOUT_MIDDLE;
        r.setAlignment(align);
        assertEquals(align, r.getAlignment());
    }

    // ---- Line tightness ----------------------------------------------

    @Test
    void defaultLineTightnessIsOne() {
        MarkupRenderer r = new MarkupRenderer();
        assertEquals(1f, r.getLineTightness(), 0.0001f);
    }

    @Test
    void setLineTightnessRoundTrips() {
        MarkupRenderer r = new MarkupRenderer();
        r.setLineTightness(0.85f);
        assertEquals(0.85f, r.getLineTightness(), 0.0001f);
    }

    // ---- Text fitting ------------------------------------------------

    @Test
    void defaultTextFittingIsNone() {
        MarkupRenderer r = new MarkupRenderer();
        assertEquals(MarkupRenderer.FIT_NONE, r.getTextFitting());
    }

    @Test
    void setTextFittingRoundTrips() {
        MarkupRenderer r = new MarkupRenderer();
        r.setTextFitting(MarkupRenderer.FIT_BOTH);
        assertEquals(MarkupRenderer.FIT_BOTH, r.getTextFitting());
    }

    // ---- Page shape --------------------------------------------------

    @Test
    void defaultPageShapeIsRectangle() {
        MarkupRenderer r = new MarkupRenderer();
        assertSame(PageShape.RECTANGLE_SHAPE, r.getPageShape());
    }

    @Test
    void setPageShapeRoundTrips() {
        MarkupRenderer r = new MarkupRenderer();
        PageShape custom = PageShape.RECTANGLE_SHAPE;  // any non-null shape
        r.setPageShape(custom);
        assertSame(custom, r.getPageShape());
    }

    // ---- Drawing (smoke test) ----------------------------------------

    @Test
    void drawPlainTextReturnsPositiveHeight() {
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText("Hello, world.");
        Rectangle2D region = new Rectangle2D.Double(0, 0, 200, 100);
        try (GraphicsHandle gh = new GraphicsHandle(300, 200)) {
            double height = r.draw(gh.g, region);
            assertTrue(Double.isFinite(height) && height > 0,
                    "expected positive finite height; got " + height);
        }
    }

    @Test
    void drawEmptyMarkupReturnsFiniteHeight() {
        // Empty markup still lays out one padding line; see
        // MarkupRendererBugsTest for the expected-zero case.
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText("");
        Rectangle2D region = new Rectangle2D.Double(0, 0, 200, 100);
        try (GraphicsHandle gh = new GraphicsHandle(300, 200)) {
            double drawn = r.draw(gh.g, region);
            assertTrue(Double.isFinite(drawn) && drawn >= 0,
                    "expected non-negative finite height; got " + drawn);
        }
    }

    @Test
    void measureReturnsHeightWithoutDrawing() {
        // measure() reports the covered height; draw() reports where
        // the next line would start, so it includes trailing leading.
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText("Hello, world.");
        Rectangle2D region = new Rectangle2D.Double(0, 0, 200, 100);
        Graphics2D spy = spyGraphics(300, 200);
        try {
            double measured = r.measure(spy, region);
            verify(spy, never()).drawGlyphVector(any(), anyFloat(), anyFloat());
            assertTrue(Double.isFinite(measured) && measured > 0,
                    "expected positive finite height; got " + measured);
            double drawn = r.draw(spy, region);
            assertTrue(measured <= drawn,
                    "expected measured height <= next-line y; measured="
                            + measured + " drawn=" + drawn);
        } finally {
            spy.dispose();
        }
    }

    @Test
    void drawAsSingleLineReturnsWidthWithinRegion() {
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText("Hello, world.");
        Rectangle2D region = new Rectangle2D.Double(0, 0, 200, 30);
        try (GraphicsHandle gh = new GraphicsHandle(300, 100)) {
            double width = r.drawAsSingleLine(gh.g, region);
            assertTrue(Double.isFinite(width) && width > 0 && width <= region.getWidth(),
                    "expected width in (0, " + region.getWidth() + "]; got " + width);
        }
    }

    @Test
    void drawAsSingleLineShrinksOverlongTextIntoRegion() {
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText(repeat("Hello, world. ", 10));
        Rectangle2D region = new Rectangle2D.Double(0, 0, 100, 30);
        try (GraphicsHandle gh = new GraphicsHandle(300, 100)) {
            double natural = r.drawAsSingleLine(gh.g, new Rectangle2D.Double(0, 0, 10_000, 30));
            assertThat(r.drawAsSingleLine(gh.g, region)).isPositive().isLessThan(natural);
        }
    }

    @Test
    void nestedTagsCombineStyles() {
        List<Font> fonts = fontsRendered("Hello, <b><i>bold italic</i></b> world.");
        assertTrue(fonts.stream().anyMatch(f -> f.isBold() && f.isItalic()),
                "expected a bold italic glyph vector; got " + fonts);
    }

    // ---- Layout-segment introspection --------------------------------
    //
    // appendSegments lets us see what the renderer would have drawn:
    // a list of LayoutSegments, each with position, line/paragraph
    // boundaries, and a TextLayout. The source text inside TextLayout
    // isn't directly retrievable, but counts, positions, character
    // counts, and structural properties are. These tests pin the
    // structural shape of the layout output.

    @Test
    void emptyMarkupMeasuresAsOneLine() {
        MarkupRenderer empty = new MarkupRenderer();
        empty.setMarkupText("");
        MarkupRenderer space = new MarkupRenderer();
        space.setMarkupText(" ");
        Rectangle2D region = new Rectangle2D.Double(0, 0, 100, 100);
        try (GraphicsHandle gh = new GraphicsHandle(200, 200)) {
            assertThat(empty.measure(gh.g, region)).isPositive().isEqualTo(space.measure(gh.g, region));
        }
    }

    @Test
    void emptyMarkupProducesSingleMinimalSegment() {
        // Empty markup still produces one segment; the layout pass
        // always emits at least one segment (with the renderer's
        // minimum-character padding) so callers have a y-baseline.
        // Pin the structural shape — segment exists, starts the line,
        // is in paragraph 0 — without committing to the exact
        // padding character count.
        List<LayoutSegment> segments = layoutOf("", 200, 100);
        assertEquals(1, segments.size(),
                "expected single segment for empty markup, got " + segments.size());
        assertTrue(segments.get(0).isStartOfLine());
        assertEquals(0, segments.get(0).getSourceParagraphIndex());
    }

    @Test
    void plainShortTextProducesAtLeastOneSegment() {
        List<LayoutSegment> segments = layoutOf("Hello world.", 200, 100);
        assertFalse(segments.isEmpty(), "expected at least one segment for non-empty markup");
        assertTrue(segments.get(0).isStartOfLine(),
                "first segment of single-line text must start a line");
    }

    @Test
    void firstSegmentStartsLine() {
        List<LayoutSegment> segments = layoutOf("Hello world.", 200, 100);
        assertTrue(segments.get(0).isStartOfLine());
    }

    @Test
    void singleParagraphSegmentsShareParagraphIndex() {
        List<LayoutSegment> segments = layoutOf("Hello world. Goodbye world.", 200, 100);
        int firstParagraph = segments.get(0).getSourceParagraphIndex();
        for (LayoutSegment s : segments) {
            assertEquals(firstParagraph, s.getSourceParagraphIndex(),
                    "single-paragraph layout must have all segments in same paragraph");
        }
    }

    @Test
    void longTextWrapsAcrossMultipleLines() {
        // A region narrow enough that the text cannot fit on one line.
        String markup = "This is a longer piece of text that should wrap "
                + "across multiple lines when laid out in a narrow region.";
        List<LayoutSegment> segments = layoutOf(markup, 80, 200);
        long lineStarts = segments.stream().filter(LayoutSegment::isStartOfLine).count();
        assertTrue(lineStarts >= 2,
                "expected at least 2 line-start segments for wrapped text, got " + lineStarts);
    }

    @Test
    void wrappedSegmentsHaveIncreasingYOffsets() {
        String markup = "This is a longer piece of text that should wrap "
                + "across multiple lines when laid out in a narrow region.";
        List<LayoutSegment> segments = layoutOf(markup, 80, 200);
        float prevY = Float.NEGATIVE_INFINITY;
        for (LayoutSegment s : segments) {
            if (s.isStartOfLine()) {
                assertTrue(s.getYOffset() >= prevY,
                        "line-start y-offsets must be non-decreasing; got " + s.getYOffset()
                                + " after " + prevY);
                prevY = s.getYOffset();
            }
        }
    }

    @Test
    void styleTagPreservesTotalCharacterCount() {
        // The styled and unstyled forms should cover the same number
        // of source characters in their TextLayouts (the tags
        // themselves are consumed by the parser, not rendered).
        List<LayoutSegment> plain = layoutOf("Hello world.", 300, 100);
        List<LayoutSegment> styled = layoutOf("Hello <b>brave</b> world.", 300, 100);

        int plainChars = totalCharacters(plain);
        int styledChars = totalCharacters(styled);
        // styled has "brave" (5) vs plain having nothing for the tag
        // — so styled total = plain total + 5 (approximately, modulo
        // whitespace handling).
        assertTrue(styledChars > plainChars,
                "styled text with additional content should have more characters; "
                        + "plain=" + plainChars + " styled=" + styledChars);
    }

    @Test
    void unknownTagDoesNotConsumeContent() {
        // Unknown tags are handled by handleUnknownTag; the base
        // implementation drops the tag but the content between
        // open/close should still appear in the layout.
        List<LayoutSegment> segments = layoutOf("Hello <unknown>brave</unknown> world.", 300, 100);
        int chars = totalCharacters(segments);
        // The visible text is "Hello brave world." = 18 chars, but
        // character counts in TextLayouts may include trailing space
        // handling. Just verify we have a non-trivial count.
        assertTrue(chars >= "Hello  world.".length(),
                "expected unknown-tag content to be preserved in layout; got " + chars);
    }

    @Test
    void replacementTagSubstitutionAppearsInLayout() {
        MarkupRenderer r = new MarkupRenderer();
        r.setReplacementForTag("name", "Alice");
        r.setMarkupText("Hello, <name>.");
        List<LayoutSegment> segments = new ArrayList<>();
        try (GraphicsHandle gh = new GraphicsHandle(400, 200)) {
            r.appendSegments(gh.g, new Rectangle2D.Double(0, 0, 300, 100), segments);
        }
        int chars = totalCharacters(segments);
        // "Hello, Alice." = 13 chars (vs "Hello, ." = 8 if the
        // replacement was dropped).
        assertTrue(chars >= "Hello, Alice.".length() - 1,
                "expected replacement text to be substituted into layout; "
                        + "char count = " + chars);
    }

    // ---- Layout helpers ----------------------------------------------

    /**
     * Runs the renderer over {@code markup} into a region of the given
     * dimensions and returns the resulting segment list. Uses a fresh
     * renderer with defaults (72 dpi, Serif 9pt). The graphics buffer
     * is large enough to back any reasonable layout request.
     */
    private static List<LayoutSegment> layoutOf(String markup, double w, double h) {
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText(markup);
        List<LayoutSegment> segments = new ArrayList<>();
        try (GraphicsHandle gh = new GraphicsHandle(
                Math.max((int) w + 100, 200),
                Math.max((int) h + 100, 200))) {
            r.appendSegments(gh.g, new Rectangle2D.Double(0, 0, w, h), segments);
        }
        return segments;
    }

    private static int totalCharacters(List<LayoutSegment> segments) {
        int total = 0;
        for (LayoutSegment s : segments) {
            // Reach into the segment's TextLayout — exposed via the
            // public draw() but not via a getter. We can use the
            // segment's advance + leading as an indirect signal, but
            // for character counts we need the TextLayout itself.
            // The field is package-private; use the layout's own
            // accessors via a small reflective hop kept local to the
            // test.
            total += textLayoutOf(s).getCharacterCount();
        }
        return total;
    }

    private static java.awt.font.TextLayout textLayoutOf(LayoutSegment s) {
        try {
            var f = LayoutSegment.class.getDeclaredField("layout");
            f.setAccessible(true);
            return (java.awt.font.TextLayout) f.get(s);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("could not reach LayoutSegment.layout", ex);
        }
    }

    // ---- Drawing operations (recording Graphics2D) -------------------
    //
    // Spies on a real Graphics2D and inspects drawGlyphVector calls.
    // TextLayout renders via drawGlyphVector(GlyphVector, x, y); each
    // call carries a font (via GlyphVector.getFont()) and a glyph
    // count. Style-tag transitions show up as font changes; total
    // glyph count tracks the rendered character count.

    @Test
    void plainTextRendersViaGlyphVectors() {
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText("Hello world.");

        Graphics2D spy = spyGraphics(300, 200);
        try {
            r.draw(spy, new Rectangle2D.Double(0, 0, 250, 100));

            ArgumentCaptor<GlyphVector> gv = ArgumentCaptor.forClass(GlyphVector.class);
            verify(spy, atLeast(1)).drawGlyphVector(gv.capture(), anyFloat(), anyFloat());
            assertFalse(gv.getAllValues().isEmpty());
        } finally {
            spy.dispose();
        }
    }

    @Test
    void styleTagSwitchesToBoldFont() {
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText("Hello <b>brave</b> world.");

        Graphics2D spy = spyGraphics(300, 200);
        try {
            r.draw(spy, new Rectangle2D.Double(0, 0, 250, 100));

            ArgumentCaptor<GlyphVector> gv = ArgumentCaptor.forClass(GlyphVector.class);
            verify(spy, atLeast(1)).drawGlyphVector(gv.capture(), anyFloat(), anyFloat());

            boolean hasBold = gv.getAllValues().stream()
                    .map(GlyphVector::getFont)
                    .anyMatch(Font::isBold);
            assertTrue(hasBold,
                    "expected at least one bold glyph vector for <b>brave</b> content");
        } finally {
            spy.dispose();
        }
    }

    @Test
    void italicTagSwitchesToItalicFont() {
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText("Hello <i>brave</i> world.");

        Graphics2D spy = spyGraphics(300, 200);
        try {
            r.draw(spy, new Rectangle2D.Double(0, 0, 250, 100));

            ArgumentCaptor<GlyphVector> gv = ArgumentCaptor.forClass(GlyphVector.class);
            verify(spy, atLeast(1)).drawGlyphVector(gv.capture(), anyFloat(), anyFloat());

            boolean hasItalic = gv.getAllValues().stream()
                    .map(GlyphVector::getFont)
                    .anyMatch(Font::isItalic);
            assertTrue(hasItalic,
                    "expected at least one italic glyph vector for <i>brave</i> content");
        } finally {
            spy.dispose();
        }
    }

    @Test
    void plainTextHasNoBoldOrItalicGlyphs() {
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText("Hello world.");

        Graphics2D spy = spyGraphics(300, 200);
        try {
            r.draw(spy, new Rectangle2D.Double(0, 0, 250, 100));

            ArgumentCaptor<GlyphVector> gv = ArgumentCaptor.forClass(GlyphVector.class);
            verify(spy, atLeast(1)).drawGlyphVector(gv.capture(), anyFloat(), anyFloat());

            for (GlyphVector v : gv.getAllValues()) {
                Font f = v.getFont();
                assertFalse(f.isBold(),
                        "unexpected bold font in plain-text output: " + f);
                assertFalse(f.isItalic(),
                        "unexpected italic font in plain-text output: " + f);
            }
        } finally {
            spy.dispose();
        }
    }

    @Test
    void styleTagProducesMoreGlyphVectorCallsThanPlainText() {
        // A style boundary forces the layout engine to split the run,
        // so a tagged version produces more drawGlyphVector calls
        // than the same content rendered plain.
        int plainCalls = countGlyphVectorCalls("Hello brave world.");
        int taggedCalls = countGlyphVectorCalls("Hello <b>brave</b> world.");
        assertTrue(taggedCalls > plainCalls,
                "expected style boundary to increase glyph-vector call count: "
                        + "plain=" + plainCalls + " tagged=" + taggedCalls);
    }

    @Test
    void totalGlyphCountTracksVisibleContent() {
        // <b>brave</b> contributes 5 visible glyphs beyond what
        // "Hello  world." would render. Tag markup itself is consumed
        // by the parser.
        int plain = totalGlyphsRendered("Hello world.");
        int tagged = totalGlyphsRendered("Hello <b>brave</b> world.");
        // tagged should have plain + ~5 visible glyphs (give or take
        // a space, depending on how the parser handles whitespace
        // around the tag).
        assertTrue(tagged >= plain + 4,
                "expected tagged content to add visible glyphs: "
                        + "plain=" + plain + " tagged=" + tagged);
    }

    private int countGlyphVectorCalls(String markup) {
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText(markup);
        Graphics2D spy = spyGraphics(400, 200);
        try {
            r.draw(spy, new Rectangle2D.Double(0, 0, 300, 100));
            ArgumentCaptor<GlyphVector> gv = ArgumentCaptor.forClass(GlyphVector.class);
            verify(spy, atLeast(0)).drawGlyphVector(gv.capture(), anyFloat(), anyFloat());
            return gv.getAllValues().size();
        } finally {
            spy.dispose();
        }
    }

    private int totalGlyphsRendered(String markup) {
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText(markup);
        Graphics2D spy = spyGraphics(400, 200);
        try {
            r.draw(spy, new Rectangle2D.Double(0, 0, 300, 100));
            ArgumentCaptor<GlyphVector> gv = ArgumentCaptor.forClass(GlyphVector.class);
            verify(spy, atLeast(0)).drawGlyphVector(gv.capture(), anyFloat(), anyFloat());
            return gv.getAllValues().stream()
                    .mapToInt(GlyphVector::getNumGlyphs)
                    .sum();
        } finally {
            spy.dispose();
        }
    }

    private List<Font> fontsRendered(String markup) {
        return fontsRendered(new MarkupRenderer(), markup);
    }

    private List<Font> fontsRendered(MarkupRenderer r, String markup) {
        r.setMarkupText(markup);
        Graphics2D spy = spyGraphics(400, 200);
        try {
            r.draw(spy, new Rectangle2D.Double(0, 0, 300, 100));
            ArgumentCaptor<GlyphVector> gv = ArgumentCaptor.forClass(GlyphVector.class);
            verify(spy, atLeast(0)).drawGlyphVector(gv.capture(), anyFloat(), anyFloat());
            return gv.getAllValues().stream()
                    .map(GlyphVector::getFont)
                    .toList();
        } finally {
            spy.dispose();
        }
    }

    private static Graphics2D spyGraphics(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        return spy(img.createGraphics());
    }

    // ---- Markup syntax edge cases -----------------------------------
    //
    // Parser robustness: malformed input, escape sequences, autoclose
    // mode, inline control tags (<tight>, <loose>, <autoclose>,
    // <manualclose>). These tests pin the parser's tolerance for
    // edge-case input.

    @Test
    void ltEscapeProducesLiteralOpenAngleBracket() {
        // <lt> is special-cased in the parser to insert "<" without
        // re-parsing. Verify the rendered output has at least one
        // more glyph than the same markup with the <lt> removed.
        int withLt = totalGlyphsRendered("a<lt>b");
        int withoutLt = totalGlyphsRendered("ab");
        assertEquals(withoutLt + 1, withLt,
                "expected <lt> to add exactly one literal '<' glyph; "
                        + "without=" + withoutLt + " with=" + withLt);
    }

    @Test
    void gtEscapeProducesLiteralCloseAngleBracket() {
        int withGt = totalGlyphsRendered("a<gt>b");
        int withoutGt = totalGlyphsRendered("ab");
        assertEquals(withoutGt + 1, withGt,
                "expected <gt> to add exactly one literal '>' glyph; "
                        + "without=" + withoutGt + " with=" + withGt);
    }

    @Test
    void setAutocloseDefaultIsFalse() {
        // The default behavior is manual-close (open tags persist
        // across paragraph breaks).
        MarkupRenderer r = new MarkupRenderer();
        assertFalse(r.getAutoclose());
    }

    @Test
    void setAutocloseTrueRoundTrips() {
        MarkupRenderer r = new MarkupRenderer();
        r.setAutoclose(true);
        assertTrue(r.getAutoclose());
    }

    @Test
    void mismatchedClosingTagIsIgnored() {
        // </i> closes nothing, so bold continues to the end.
        assertEquals(totalGlyphsRendered("foo bar"), totalGlyphsRendered("<b>foo</i> bar"));
        List<Font> fonts = fontsRendered("<b>foo</i> bar");
        assertTrue(fonts.stream().allMatch(Font::isBold),
                "expected bold to run to the end; got " + fonts);
    }

    @Test
    void unclosedTagAppliesToEnd() {
        assertEquals(totalGlyphsRendered("foo"), totalGlyphsRendered("<b>foo"));
        List<Font> fonts = fontsRendered("<b>foo");
        assertTrue(fonts.stream().allMatch(Font::isBold),
                "expected unclosed <b> to apply to the end; got " + fonts);
    }

    @Test
    void emptyTagIsDropped() {
        assertEquals(totalGlyphsRendered("ab"), totalGlyphsRendered("a<>b"));
    }

    @Test
    void unterminatedTagDropsRestOfLine() {
        assertEquals(totalGlyphsRendered("plain "), totalGlyphsRendered("plain <b unterminated"));
    }

    @Test
    void inlineAutocloseTagStopsStylesAtLineEnd() {
        MarkupRenderer r = new MarkupRenderer();
        r.setAutoclose(false);
        List<Font> fonts = fontsRendered(r, "<autoclose><b>foo\nbar");
        assertTrue(fonts.get(0).isBold(), "expected first line bold; got " + fonts);
        assertFalse(fonts.get(fonts.size() - 1).isBold(),
                "expected <b> to close at end of first line; got " + fonts);
    }

    @Test
    void inlineManualcloseTagCarriesStylesAcrossLines() {
        MarkupRenderer r = new MarkupRenderer();
        r.setAutoclose(true);
        List<Font> fonts = fontsRendered(r, "<manualclose><b>foo\nbar");
        assertTrue(fonts.stream().allMatch(Font::isBold),
                "expected <b> to carry over to the second line; got " + fonts);
    }

    @Test
    void setAutocloseControlsStyleCarryOver() {
        MarkupRenderer auto = new MarkupRenderer();
        auto.setAutoclose(true);
        assertFalse(fontsRendered(auto, "<b>foo\nbar").stream().allMatch(Font::isBold));

        MarkupRenderer manual = new MarkupRenderer();
        manual.setAutoclose(false);
        assertTrue(fontsRendered(manual, "<b>foo\nbar").stream().allMatch(Font::isBold));
    }

    @Test
    void tightTagRemovesParagraphLeading() {
        double normal = secondParagraphY("first\nsecond");
        double tight = secondParagraphY("<tight>first\nsecond");
        assertTrue(tight < normal,
                "expected <tight> to reduce paragraph gap; normal=" + normal + " tight=" + tight);
    }

    @Test
    void looseTagAddsParagraphLeading() {
        double normal = secondParagraphY("first\nsecond");
        double loose = secondParagraphY("<loose>first\nsecond");
        assertTrue(loose > normal,
                "expected <loose> to widen paragraph gap; normal=" + normal + " loose=" + loose);
    }

    private static double secondParagraphY(String markup) {
        return layoutOf(markup, 300, 200).stream()
                .filter(s -> s.getSourceParagraphIndex() == 1)
                .mapToDouble(LayoutSegment::getYOffset)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no second paragraph in " + markup));
    }

    @Test
    void backslashLineContinuation() {
        // A backslash followed by a newline suppresses the line
        // break — the renderer's TEXTBS state consumes whitespace
        // after backslash. Pin the behavior: backslash + newline
        // produces one paragraph, not two.
        List<LayoutSegment> withBackslash = layoutOf("first\\\nsecond", 300, 200);
        int maxParaWithBackslash = withBackslash.stream()
                .mapToInt(LayoutSegment::getSourceParagraphIndex)
                .max().orElse(0);

        List<LayoutSegment> plain = layoutOf("first\nsecond", 300, 200);
        int maxParaPlain = plain.stream()
                .mapToInt(LayoutSegment::getSourceParagraphIndex)
                .max().orElse(0);

        assertTrue(maxParaWithBackslash < maxParaPlain,
                "expected backslash+newline to suppress paragraph break; "
                        + "plain paragraphs=" + (maxParaPlain + 1)
                        + " with-backslash=" + (maxParaWithBackslash + 1));
    }

    @Test
    void deeplyNestedTagsRenderAllContent() {
        String markup = "<b><i><u><sup>deeply</sup></u></i></b>";
        assertEquals(totalGlyphsRendered("deeply"), totalGlyphsRendered(markup));
        List<Font> fonts = fontsRendered(markup);
        assertTrue(fonts.stream().allMatch(f -> f.isBold() && f.isItalic()),
                "expected nested styles to combine; got " + fonts);
    }

    // ---- Punctuation replacement ------------------------------------
    //
    // Straight quotes, hyphen runs, and period runs are replaced with
    // typographic forms as the parser emits text. Assertions read the
    // parsed paragraphs directly.

    @Test
    void doubleQuotesBecomeCurly() {
        assertEquals("“Hi”", parsedText("\"Hi\""));
    }

    @Test
    void singleQuotesBecomeCurly() {
        assertEquals("‘Hi’", parsedText("'Hi'"));
    }

    @Test
    void apostropheBecomesRightSingleQuote() {
        assertEquals("don’t", parsedText("don't"));
    }

    @Test
    void leadingApostropheIsTreatedAsOpenQuote() {
        assertEquals("‘tis", parsedText("'tis"));
    }

    @Test
    void nestedQuotesAlternate() {
        assertEquals("“she said ‘no’”",
                parsedText("\"she said 'no'\""));
    }

    @Test
    void quoteAfterDashOpensWhenNoQuoteIsOpen() {
        assertEquals("well-“maybe”", parsedText("well-\"maybe\""));
    }

    @Test
    void quoteAfterDashClosesWhenQuoteIsOpen() {
        assertEquals("“wait-”", parsedText("\"wait-\""));
    }

    @Test
    void quoteAfterOpenParenthesisIsTreatedAsClose() {
        // Only whitespace and dashes before a quote select the open form.
        assertEquals("(”x”)", parsedText("(\"x\")"));
    }

    @Test
    void openQuoteDepthResetsEachParagraph() {
        // The dangling open quote in the first paragraph does not make
        // the dash-adjacent quote in the second one a close quote.
        assertEquals("“a\nb-“", parsedText("\"a\nb-\""));
    }

    @Test
    void doubleHyphenBecomesEnDash() {
        assertEquals("1–2", parsedText("1--2"));
    }

    @Test
    void tripleHyphenBecomesEmDash() {
        assertEquals("a—b", parsedText("a---b"));
    }

    @Test
    void hyphenAfterEnDashBecomesEmDash() {
        assertEquals("a—b", parsedText("a–-b"));
    }

    @Test
    void singleHyphenIsUnchanged() {
        assertEquals("well-known", parsedText("well-known"));
    }

    @Test
    void quadrupleHyphenLeavesTrailingHyphen() {
        assertEquals("a—-b", parsedText("a----b"));
    }

    @Test
    void triplePeriodBecomesEllipsis() {
        assertEquals("wait…", parsedText("wait..."));
    }

    @Test
    void ellipsisAtStartOfParagraph() {
        assertEquals("…and then", parsedText("...and then"));
    }

    @Test
    void enDashAtStartOfParagraph() {
        assertEquals("– aside", parsedText("-- aside"));
    }

    @Test
    void doublePeriodIsUnchanged() {
        assertEquals("wait..", parsedText("wait.."));
    }

    @Test
    void quadruplePeriodLeavesTrailingPeriod() {
        assertEquals("wait….", parsedText("wait...."));
    }

    @Test
    void noPunctuationTagDisablesReplacement() {
        assertEquals("\"a--b...\" “c”",
                parsedText("<no punctuation>\"a--b...\"</no punctuation> \"c\""));
    }

    @Test
    void noPunctuationTagNests() {
        assertEquals("\"a\" \"b\" “c”", parsedText(
                "<no punctuation><no punctuation>\"a\"</no punctuation> \"b\""
                        + "</no punctuation> \"c\""));
    }

    @Test
    void noPunctuationTagSpansParagraphs() {
        assertEquals("a--b\nc--d", parsedText("<no punctuation>a--b\nc--d"));
    }

    @Test
    void punctuationInsideTagIsNotReplaced() {
        assertEquals("Alice", parsedText("<define who \"Alice\"><who>"));
    }

    /**
     * Parses {@code markup} and returns the resulting paragraph text,
     * one line per paragraph.
     */
    private static String parsedText(String markup) {
        return parsedText(new MarkupRenderer(), markup);
    }

    private static String parsedText(MarkupRenderer r, String markup) {
        r.setMarkupText(markup);
        try (GraphicsHandle gh = new GraphicsHandle(400, 200)) {
            r.measure(gh.g, new Rectangle2D.Double(0, 0, 300, 100));
        }
        StringBuilder b = new StringBuilder();
        for (MarkupRenderer.StyledParagraph p : r.getStyledText()) {
            if (b.length() > 0) {
                b.append('\n');
            }
            AttributedCharacterIterator it = p.getIterator();
            for (char c = it.first(); c != CharacterIterator.DONE; c = it.next()) {
                b.append(c);
            }
        }
        return b.toString();
    }

    // ---- Layout-flag matrix -----------------------------------------
    //
    // Alignment flags affect segment positions; fit flags affect how
    // long content is squeezed to fit a fixed region. These tests pin
    // the visible effects: where text ends up, and whether oversized
    // content is shrunk.

    @Test
    void leftAlignmentDrawsInLeftHalf() {
        // Horizontal alignment positions visible during layout AND
        // during draw — appendSegments shows it. Vertical alignment
        // is applied during draw, so for that we spy the draw call.
        List<LayoutSegment> segments = layoutAligned("word",
                MarkupRenderer.LAYOUT_LEFT | MarkupRenderer.LAYOUT_TOP,
                100, 50);
        float x = segments.get(0).getXOffset();
        assertTrue(x < 50,
                "expected left-aligned x in left half of 100-wide region; got " + x);
    }

    @Test
    void rightAlignmentDrawsInRightHalf() {
        List<LayoutSegment> segments = layoutAligned("word",
                MarkupRenderer.LAYOUT_RIGHT | MarkupRenderer.LAYOUT_TOP,
                100, 50);
        float x = segments.get(0).getXOffset();
        assertTrue(x > 50,
                "expected right-aligned x in right half of 100-wide region; got " + x);
    }

    @Test
    void centerAlignmentPlacesTextInMiddleBand() {
        float leftX = layoutAligned("word",
                MarkupRenderer.LAYOUT_LEFT | MarkupRenderer.LAYOUT_TOP, 100, 50)
                .get(0).getXOffset();
        float rightX = layoutAligned("word",
                MarkupRenderer.LAYOUT_RIGHT | MarkupRenderer.LAYOUT_TOP, 100, 50)
                .get(0).getXOffset();
        float centerX = layoutAligned("word",
                MarkupRenderer.LAYOUT_CENTER | MarkupRenderer.LAYOUT_TOP, 100, 50)
                .get(0).getXOffset();
        assertTrue(centerX > leftX && centerX < rightX,
                "expected center-aligned x between left=" + leftX
                        + " and right=" + rightX + "; got " + centerX);
    }

    @Test
    void topAlignmentDrawsInUpperHalf() {
        float y = drawYPosition("word",
                MarkupRenderer.LAYOUT_LEFT | MarkupRenderer.LAYOUT_TOP,
                200, 200);
        assertTrue(y < 100,
                "expected top-aligned draw y in upper half of 200-tall region; got " + y);
    }

    @Test
    void bottomAlignmentDrawsInLowerHalf() {
        float y = drawYPosition("word",
                MarkupRenderer.LAYOUT_LEFT | MarkupRenderer.LAYOUT_BOTTOM,
                200, 200);
        assertTrue(y > 100,
                "expected bottom-aligned draw y in lower half of 200-tall region; got " + y);
    }

    @Test
    void middleAlignmentDrawsBetweenTopAndBottom() {
        float topY = drawYPosition("word",
                MarkupRenderer.LAYOUT_LEFT | MarkupRenderer.LAYOUT_TOP, 200, 200);
        float bottomY = drawYPosition("word",
                MarkupRenderer.LAYOUT_LEFT | MarkupRenderer.LAYOUT_BOTTOM, 200, 200);
        float middleY = drawYPosition("word",
                MarkupRenderer.LAYOUT_LEFT | MarkupRenderer.LAYOUT_MIDDLE, 200, 200);
        assertTrue(middleY > topY && middleY < bottomY,
                "expected middle-aligned draw y between top=" + topY
                        + " and bottom=" + bottomY + "; got " + middleY);
    }

    // ---- Inline alignment tags --------------------------------------
    //
    // Horizontal tags set the alignment of the paragraph they end up
    // in; they are not scoped, so closing tags have no effect.
    // Vertical tags apply to the whole block, and the last one wins.

    private static final int LEFT_TOP = MarkupRenderer.LAYOUT_LEFT | MarkupRenderer.LAYOUT_TOP;
    private static final String WRAPPING_TEXT =
            "The quick brown fox jumps over the lazy dog again and again.";

    @Test
    void rightTagRightAligns() {
        assertEquals(firstX("word", MarkupRenderer.LAYOUT_RIGHT | MarkupRenderer.LAYOUT_TOP),
                firstX("<right>word", LEFT_TOP), 0.01);
    }

    @Test
    void centerTagCenters() {
        assertEquals(firstX("word", MarkupRenderer.LAYOUT_CENTER | MarkupRenderer.LAYOUT_TOP),
                firstX("<center>word", LEFT_TOP), 0.01);
    }

    @Test
    void centreTagIsAliasForCenter() {
        assertEquals(firstX("<center>word", LEFT_TOP), firstX("<centre>word", LEFT_TOP), 0.01);
    }

    @Test
    void leftTagOverridesDefaultAlignment() {
        assertEquals(firstX("word", LEFT_TOP),
                firstX("<left>word", MarkupRenderer.LAYOUT_RIGHT | MarkupRenderer.LAYOUT_TOP), 0.01);
    }

    @Test
    void alignmentTagsAreCaseInsensitive() {
        assertEquals(firstX("<right>word", LEFT_TOP), firstX("<RIGHT>word", LEFT_TOP), 0.01);
    }

    @Test
    void alignmentTagMidParagraphAppliesToWholeParagraph() {
        assertEquals(firstX("<right>word more", LEFT_TOP),
                firstX("word <right>more", LEFT_TOP), 0.01);
    }

    @Test
    void alignmentTagAppliesFromItsParagraphOnward() {
        List<LayoutSegment> segments = layoutAligned("a\n<right>b\nc", LEFT_TOP, 100, 100);
        assertTrue(paragraphX(segments, 0) < 50, "first paragraph should stay left");
        assertTrue(paragraphX(segments, 1) > 50, "second paragraph should be right");
        assertTrue(paragraphX(segments, 2) > 50, "third paragraph should inherit right");
    }

    @Test
    void closingAlignmentTagHasNoEffect() {
        List<LayoutSegment> segments = layoutAligned("<right>a</right>\nb", LEFT_TOP, 100, 100);
        assertTrue(paragraphX(segments, 1) > 50,
                "expected </right> to leave right alignment in place");
    }

    @Test
    void bottomTagBottomAligns() {
        assertEquals(drawYPosition("word", MarkupRenderer.LAYOUT_LEFT | MarkupRenderer.LAYOUT_BOTTOM, 200, 200),
                drawYPosition("<bottom>word", LEFT_TOP, 200, 200), 0.01);
    }

    @Test
    void middleTagCentersVertically() {
        assertEquals(drawYPosition("word", MarkupRenderer.LAYOUT_LEFT | MarkupRenderer.LAYOUT_MIDDLE, 200, 200),
                drawYPosition("<middle>word", LEFT_TOP, 200, 200), 0.01);
    }

    @Test
    void topTagOverridesDefaultVerticalAlignment() {
        assertEquals(drawYPosition("word", LEFT_TOP, 200, 200),
                drawYPosition("<top>word", MarkupRenderer.LAYOUT_LEFT | MarkupRenderer.LAYOUT_BOTTOM, 200, 200),
                0.01);
    }

    @Test
    void lastVerticalTagAppliesToWholeBlock() {
        assertEquals(drawYPosition("a\nb", LEFT_TOP, 200, 200),
                drawYPosition("<bottom>a\n<top>b", LEFT_TOP, 200, 200), 0.01);
    }

    @Test
    void bottomAlignedOverflowIsClampedToTop() {
        // Text taller than the region starts at the top rather than
        // being pushed above it.
        String tall = repeat("line\n", 20);
        assertEquals(drawYPosition(tall, LEFT_TOP, 100, 50),
                drawYPosition(tall, MarkupRenderer.LAYOUT_LEFT | MarkupRenderer.LAYOUT_BOTTOM, 100, 50),
                0.01);
    }

    @Test
    void singleLineHonorsVerticalAlignment() {
        float top = singleLineY("<top>word", 200, 200);
        float middle = singleLineY("<middle>word", 200, 200);
        float bottom = singleLineY("<bottom>word", 200, 200);
        assertTrue(top < middle && middle < bottom,
                "expected top < middle < bottom; got " + top + ", " + middle + ", " + bottom);
    }

    @Test
    void singleLineTallerThanRegionIsClampedToTop() {
        assertEquals(singleLineY("<top>word", 100, 2),
                singleLineY("<bottom>word", 100, 2), 0.01);
    }

    @Test
    void justifiedTagFillsAllButLastLine() {
        List<Double> widths = lineWidths("<justified>" + WRAPPING_TEXT, LEFT_TOP);
        assertTrue(widths.size() >= 3, "need several lines; got " + widths);
        for (double w : widths.subList(0, widths.size() - 1)) {
            assertEquals(100, w, 1.0, "expected justified line to fill region; got " + widths);
        }
        assertTrue(widths.get(widths.size() - 1) < 99,
                "expected last line to stay ragged; got " + widths);
    }

    @Test
    void textIsRaggedByDefault() {
        List<Double> widths = lineWidths(WRAPPING_TEXT, LEFT_TOP);
        assertTrue(widths.subList(0, widths.size() - 1).stream().anyMatch(w -> w < 99),
                "expected at least one short line; got " + widths);
    }

    @Test
    void justifyFlagMatchesJustifiedTag() {
        assertEquals(lineWidths("<justified>" + WRAPPING_TEXT, LEFT_TOP),
                lineWidths(WRAPPING_TEXT, LEFT_TOP | MarkupRenderer.LAYOUT_JUSTIFY));
    }

    @Test
    void raggedTagDisablesJustifyFlag() {
        assertEquals(lineWidths(WRAPPING_TEXT, LEFT_TOP),
                lineWidths("<ragged>" + WRAPPING_TEXT, LEFT_TOP | MarkupRenderer.LAYOUT_JUSTIFY));
    }

    @Test
    void horizontalTagPreservesJustification() {
        assertEquals(lineWidths("<justified>" + WRAPPING_TEXT, LEFT_TOP),
                lineWidths("<justified><right><left>" + WRAPPING_TEXT, LEFT_TOP));
    }

    @Test
    void laterJustifiedTagDoesNotJustifyEarlierParagraph() {
        String markup = WRAPPING_TEXT + "\n<justified>" + WRAPPING_TEXT;
        assertThat(lineWidths(markup, LEFT_TOP, 0).get(0)).isLessThan(99d);
        assertThat(lineWidths(markup, LEFT_TOP, 1).get(0)).isCloseTo(100d, within(1d));
    }

    @Test
    void laterRaggedTagDoesNotUnjustifyEarlierParagraph() {
        String markup = "<justified>" + WRAPPING_TEXT + "\n<ragged>" + WRAPPING_TEXT;
        assertThat(lineWidths(markup, LEFT_TOP, 0).get(0)).isCloseTo(100d, within(1d));
        assertThat(lineWidths(markup, LEFT_TOP, 1).get(0)).isLessThan(99d);
    }

    @Test
    void justificationPersistsIntoLaterParagraphs() {
        String markup = "<justified>" + WRAPPING_TEXT + "\n" + WRAPPING_TEXT;
        assertThat(lineWidths(markup, LEFT_TOP, 1).get(0)).isCloseTo(100d, within(1d));
    }

    @Test
    void justifiedTagMidParagraphAppliesToWholeParagraph() {
        String markup = WRAPPING_TEXT + "<justified>";
        assertThat(lineWidths(markup, LEFT_TOP, 0).get(0)).isCloseTo(100d, within(1d));
    }

    @Test
    void justifyFlagAppliesToEveryParagraph() {
        int justified = LEFT_TOP | MarkupRenderer.LAYOUT_JUSTIFY;
        String markup = WRAPPING_TEXT + "\n" + WRAPPING_TEXT;
        assertThat(lineWidths(markup, justified, 0).get(0)).isCloseTo(100d, within(1d));
        assertThat(lineWidths(markup, justified, 1).get(0)).isCloseTo(100d, within(1d));
    }

    @Test
    void singleWordDrawReturnsPositionBelowItsText() {
        Rectangle2D region = new Rectangle2D.Double(0, 10, 100, 100);
        MarkupRenderer plain = new MarkupRenderer();
        plain.setMarkupText("Word");
        double plainNext;
        try (GraphicsHandle gh = new GraphicsHandle(400, 200)) {
            plainNext = plain.draw(gh.g, region);
        }
        MarkupRenderer scaled = new MarkupRenderer();
        scaled.setTextFitting(MarkupRenderer.FIT_SCALE_TEXT);
        scaled.setMarkupText("Word");
        Graphics2D spy = spyGraphics(400, 200);
        try {
            double next = scaled.draw(spy, region);
            ArgumentCaptor<Float> y = ArgumentCaptor.forClass(Float.class);
            verify(spy, atLeast(1)).drawGlyphVector(any(GlyphVector.class), anyFloat(), y.capture());
            assertThat(next).isGreaterThan(y.getValue()).isLessThanOrEqualTo(plainNext);
        } finally {
            spy.dispose();
        }
    }

    @Test
    void bottomAlignedSingleWordDrawReturnsRegionBottom() {
        MarkupRenderer r = new MarkupRenderer();
        r.setAlignment(MarkupRenderer.LAYOUT_LEFT | MarkupRenderer.LAYOUT_BOTTOM);
        r.setTextFitting(MarkupRenderer.FIT_SCALE_TEXT);
        r.setMarkupText("Word");
        try (GraphicsHandle gh = new GraphicsHandle(400, 200)) {
            assertThat(r.draw(gh.g, new Rectangle2D.Double(0, 10, 100, 100))).isCloseTo(110d, within(0.01d));
        }
    }

    private static float singleLineY(String markup, double w, double h) {
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText(markup);
        Graphics2D spy = spyGraphics((int) w + 100, (int) h + 100);
        try {
            r.drawAsSingleLine(spy, new Rectangle2D.Double(0, 0, w, h));
            ArgumentCaptor<Float> y = ArgumentCaptor.forClass(Float.class);
            verify(spy, atLeast(1)).drawGlyphVector(any(GlyphVector.class), anyFloat(), y.capture());
            return y.getAllValues().get(0);
        } finally {
            spy.dispose();
        }
    }

    private static float firstX(String markup, int align) {
        return layoutAligned(markup, align, 100, 50).get(0).getXOffset();
    }

    private static float paragraphX(List<LayoutSegment> segments, int paragraph) {
        return segments.stream()
                .filter(s -> s.getSourceParagraphIndex() == paragraph)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no paragraph " + paragraph))
                .getXOffset();
    }

    private static List<Double> lineWidths(String markup, int align) {
        return lineWidths(markup, align, -1);
    }

    /**
     * Visible advance of each laid-out line in a 100-wide region,
     * optionally restricted to one source paragraph.
     */
    private static List<Double> lineWidths(String markup, int align, int paragraph) {
        return layoutAligned(markup, align, 100, 400).stream()
                .filter(LayoutSegment::isStartOfLine)
                .filter(s -> paragraph < 0 || s.getSourceParagraphIndex() == paragraph)
                .map(s -> (double) textLayoutOf(s).getVisibleAdvance())
                .toList();
    }

    /**
     * Returns the y-position passed to the first
     * {@code drawGlyphVector} call when laying out {@code markup} with
     * the given alignment into a region of the given dimensions.
     */
    private static float drawYPosition(String markup, int align,
            double w, double h) {
        MarkupRenderer r = new MarkupRenderer();
        r.setAlignment(align);
        r.setMarkupText(markup);
        Graphics2D spy = spyGraphics((int) w + 100, (int) h + 100);
        try {
            r.draw(spy, new Rectangle2D.Double(0, 0, w, h));
            ArgumentCaptor<Float> yCaptor = ArgumentCaptor.forClass(Float.class);
            verify(spy, atLeast(1)).drawGlyphVector(
                    any(GlyphVector.class), anyFloat(), yCaptor.capture());
            return yCaptor.getAllValues().get(0);
        } finally {
            spy.dispose();
        }
    }

    @Test
    void fitNoneAllowsOverflow() {
        // With FIT_NONE, long content can render past the region
        // bottom — that is, the last segment's yBottom > region
        // bottom. Use enough content to overflow a small region.
        MarkupRenderer r = new MarkupRenderer();
        r.setTextFitting(MarkupRenderer.FIT_NONE);
        r.setMarkupText(repeat("This is a long sentence. ", 20));

        List<LayoutSegment> segments = new ArrayList<>();
        try (GraphicsHandle gh = new GraphicsHandle(200, 200)) {
            r.appendSegments(gh.g, new Rectangle2D.Double(0, 0, 100, 50), segments);
        }
        float maxY = (float) segments.stream()
                .mapToDouble(LayoutSegment::getYBottom)
                .max().orElse(0);
        assertTrue(maxY > 50f,
                "expected FIT_NONE to allow overflow past region bottom (50); "
                        + "max yBottom = " + maxY);
    }

    @Test
    void fitScaleTextReducesContentExtent() {
        // FIT_SCALE_TEXT applies a TRANSFORM attribute that shrinks
        // the rendered output; Font.getSize2D() is unchanged. The
        // observable effect is that the glyph vectors' bounding
        // boxes shrink (they include the transform). Compare total
        // visual width across all glyph vectors.
        String longText = repeat("This is a long sentence. ", 20);

        double extentFitNone = totalVisualWidth(longText, MarkupRenderer.FIT_NONE, 100, 50);
        double extentFitScale = totalVisualWidth(longText, MarkupRenderer.FIT_SCALE_TEXT, 100, 50);
        assertTrue(extentFitScale < extentFitNone,
                "expected FIT_SCALE_TEXT to reduce visual extent; "
                        + "fitNone=" + extentFitNone + " fitScale=" + extentFitScale);
    }

    @Test
    void fitTightenLineSpacingReducesLineGap() {
        // With FIT_TIGHTEN_LINE_SPACING, lines should pack tighter
        // when content overflows. Compare line spacing (gap between
        // line y-offsets) between FIT_NONE and FIT_TIGHTEN_LINE_SPACING.
        String longText = repeat("Line ", 30);

        double spacingFitNone = averageLineGap(longText, MarkupRenderer.FIT_NONE, 100, 30);
        double spacingFitTight = averageLineGap(longText, MarkupRenderer.FIT_TIGHTEN_LINE_SPACING, 100, 30);
        assertTrue(spacingFitTight < spacingFitNone,
                "expected FIT_TIGHTEN_LINE_SPACING to reduce line gap; "
                        + "fitNone=" + spacingFitNone + " fitTight=" + spacingFitTight);
    }

    @Test
    void fitBothAppliesScalingAndTightening() {
        // FIT_BOTH = FIT_TIGHTEN_LINE_SPACING | FIT_SCALE_TEXT.
        // Verify the combined effect: total visual extent shrinks
        // more than FIT_NONE alone.
        String longText = repeat("Line of text. ", 30);

        double extentFitNone = totalVisualWidth(longText, MarkupRenderer.FIT_NONE, 100, 40);
        double extentFitBoth = totalVisualWidth(longText, MarkupRenderer.FIT_BOTH, 100, 40);
        assertTrue(extentFitBoth < extentFitNone,
                "FIT_BOTH should reduce extent more than FIT_NONE; "
                        + "fitNone=" + extentFitNone + " fitBoth=" + extentFitBoth);
    }

    /**
     * Sums the visual bounds widths of all glyph vectors drawn — this
     * captures both font-size changes and TRANSFORM-attribute scaling
     * applied by FIT_SCALE_TEXT.
     */
    private static double totalVisualWidth(String markup, int fitMode,
            double w, double h) {
        MarkupRenderer r = new MarkupRenderer();
        r.setTextFitting(fitMode);
        r.setMarkupText(markup);
        Graphics2D spy = spyGraphics((int) w + 200, (int) h + 200);
        try {
            r.draw(spy, new Rectangle2D.Double(0, 0, w, h));
            ArgumentCaptor<GlyphVector> gv = ArgumentCaptor.forClass(GlyphVector.class);
            verify(spy, atLeast(0)).drawGlyphVector(gv.capture(), anyFloat(), anyFloat());
            return gv.getAllValues().stream()
                    .mapToDouble(v -> v.getVisualBounds().getWidth())
                    .sum();
        } finally {
            spy.dispose();
        }
    }

    private static List<LayoutSegment> layoutAligned(String markup, int align,
            double w, double h) {
        MarkupRenderer r = new MarkupRenderer();
        r.setAlignment(align);
        r.setMarkupText(markup);
        List<LayoutSegment> segments = new ArrayList<>();
        try (GraphicsHandle gh = new GraphicsHandle((int) w + 100, (int) h + 100)) {
            r.appendSegments(gh.g, new Rectangle2D.Double(0, 0, w, h), segments);
        }
        return segments;
    }

    private static double averageLineGap(String markup, int fitMode,
            double w, double h) {
        MarkupRenderer r = new MarkupRenderer();
        r.setTextFitting(fitMode);
        r.setMarkupText(markup);
        List<LayoutSegment> segments = new ArrayList<>();
        try (GraphicsHandle gh = new GraphicsHandle((int) w + 200, (int) h + 200)) {
            r.appendSegments(gh.g, new Rectangle2D.Double(0, 0, w, h), segments);
        }
        List<Double> lineYs = new ArrayList<>();
        for (LayoutSegment s : segments) {
            if (s.isStartOfLine()) {
                lineYs.add((double) s.getYOffset());
            }
        }
        if (lineYs.size() < 2) {
            return 0;
        }
        double total = 0;
        for (int i = 1; i < lineYs.size(); i++) {
            total += lineYs.get(i) - lineYs.get(i - 1);
        }
        return total / (lineYs.size() - 1);
    }

    private static String repeat(String s, int times) {
        StringBuilder sb = new StringBuilder(s.length() * times);
        for (int i = 0; i < times; i++) {
            sb.append(s);
        }
        return sb.toString();
    }

    // ---- Built-in / replacement tags --------------------------------
    //
    // Built-ins fall into a few categories:
    //   - <define name content>           creates a user replacement
    //   - <br>                            line break (paragraph split)
    //   - <h1>...<h7>                     headlines (also split paragraphs)
    //   - <u+XXXX>                        Unicode codepoint
    //   - <image FILE ...>                inline image (via graphicStyle)
    //   - default replacements (typographic) like <infinity>, <emdash>,
    //     <nbsp>, <...>, smart-quote tags

    @Test
    void defaultReplacementsAreRegistered() {
        MarkupRenderer r = new MarkupRenderer();
        assertEquals("∞", r.getReplacementForTag("infinity"));
        assertEquals("—", r.getReplacementForTag("emdash"));
        assertEquals("–", r.getReplacementForTag("endash"));
        assertEquals("…", r.getReplacementForTag("..."));
        assertEquals(" ", r.getReplacementForTag("nbsp"));
        assertEquals("“", r.getReplacementForTag("lq"));
        assertEquals("”", r.getReplacementForTag("rq"));
    }

    @Test
    void defineTagCreatesUserReplacement() {
        // <define name content> registers a user-defined replacement
        // that subsequent uses of <name> expand to "content".
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText("<define greeting Hello>The <greeting>.");

        Graphics2D spy = spyGraphics(400, 200);
        try {
            r.draw(spy, new Rectangle2D.Double(0, 0, 300, 100));

            ArgumentCaptor<GlyphVector> gv = ArgumentCaptor.forClass(GlyphVector.class);
            verify(spy, atLeast(1)).drawGlyphVector(gv.capture(), anyFloat(), anyFloat());
            int totalGlyphs = gv.getAllValues().stream()
                    .mapToInt(GlyphVector::getNumGlyphs)
                    .sum();
            // "The Hello." = 10 visible glyphs (give or take); "The ."
            // alone (if the define was dropped) would be 5. Pin that
            // the substitution happened.
            assertTrue(totalGlyphs >= 9,
                    "expected <define>+<greeting> to expand to 'Hello'; "
                            + "glyph count = " + totalGlyphs);
        } finally {
            spy.dispose();
        }
    }

    @Test
    void brTagSplitsParagraphs() {
        // <br> forces a paragraph break — segments after the break
        // have a higher sourceParagraphIndex than those before.
        List<LayoutSegment> segments = layoutOf("first<br>second", 300, 200);
        int maxParagraph = segments.stream()
                .mapToInt(LayoutSegment::getSourceParagraphIndex)
                .max().orElse(0);
        assertTrue(maxParagraph >= 1,
                "expected <br> to produce a second paragraph; "
                        + "max paragraph index = " + maxParagraph);
    }

    @Test
    void newlineSplitsParagraphs() {
        // A literal newline in the markup also splits paragraphs.
        List<LayoutSegment> segments = layoutOf("first\nsecond", 300, 200);
        int maxParagraph = segments.stream()
                .mapToInt(LayoutSegment::getSourceParagraphIndex)
                .max().orElse(0);
        assertTrue(maxParagraph >= 1,
                "expected newline to produce a second paragraph; "
                        + "max paragraph index = " + maxParagraph);
    }

    @Test
    void unicodeEscapeTagInsertsCodepoint() {
        // <u+221E> inserts ∞ (infinity). handleUnknownTag handles this.
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText("Hello <u+221E> world.");

        Graphics2D spy = spyGraphics(400, 200);
        try {
            r.draw(spy, new Rectangle2D.Double(0, 0, 300, 100));

            ArgumentCaptor<GlyphVector> gv = ArgumentCaptor.forClass(GlyphVector.class);
            verify(spy, atLeast(1)).drawGlyphVector(gv.capture(), anyFloat(), anyFloat());
            int totalGlyphs = gv.getAllValues().stream()
                    .mapToInt(GlyphVector::getNumGlyphs)
                    .sum();
            // "Hello ∞ world." vs "Hello  world." (if escape ignored)
            // — substituted version has the same character count, so
            // we mainly verify "doesn't throw + renders".
            assertTrue(totalGlyphs > 0,
                    "expected u+221E to render; got " + totalGlyphs + " glyphs");
        } finally {
            spy.dispose();
        }
    }

    @Test
    void unknownTagWithoutHandlerIsDropped() {
        // A genuinely unknown tag (handleUnknownTag returns null) is
        // dropped from the output. The surrounding text still renders.
        // Compare "before after" with and without the unknown tag.
        int plain = totalGlyphsRendered("Before After");
        int withUnknown = totalGlyphsRendered("Before <xyzzy>After");
        assertEquals(plain, withUnknown,
                "expected unknown tag to be dropped; plain=" + plain
                        + " withUnknown=" + withUnknown);
    }

    @Test
    void supTagRendersAsBuiltInStyle() {
        // <sup> superscript is one of the typographic non-parametric
        // tags. It changes the font metrics for the enclosed run.
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText("E = mc<sup>2</sup>");

        Graphics2D spy = spyGraphics(400, 200);
        try {
            r.draw(spy, new Rectangle2D.Double(0, 0, 300, 100));

            ArgumentCaptor<GlyphVector> gv = ArgumentCaptor.forClass(GlyphVector.class);
            verify(spy, atLeast(2)).drawGlyphVector(gv.capture(), anyFloat(), anyFloat());

            // Superscript content typically renders at a smaller size
            // OR at a higher position. We can't reliably verify the
            // exact transform, but we can verify that more than one
            // glyph-vector call happened (style boundary forced a
            // split), confirming the tag was recognized.
            assertTrue(gv.getAllValues().size() >= 2,
                    "expected <sup> to split into multiple glyph vectors");
        } finally {
            spy.dispose();
        }
    }

    @Test
    void colorReplacementTagsAreRegistered() {
        // Non-parametric color tags (e.g. <red>, <blue>) are registered
        // as style tags during createDefaultStyleMap.
        MarkupRenderer r = new MarkupRenderer();
        assertNotNull(r.getStyleForTag("red"));
        assertNotNull(r.getStyleForTag("blue"));
        assertNotNull(r.getStyleForTag("green"));
        assertNotNull(r.getStyleForTag("black"));
        assertNotNull(r.getStyleForTag("white"));
    }

    @Test
    void namedColorTagAppliesColor() {
        // <red> is a non-parametric tag that applies COLOR_RED.
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText("Hello <red>red</red> world.");

        Graphics2D spy = spyGraphics(400, 200);
        try {
            r.draw(spy, new Rectangle2D.Double(0, 0, 300, 100));

            ArgumentCaptor<Paint> paintCaptor = ArgumentCaptor.forClass(Paint.class);
            verify(spy, atLeast(1)).setPaint(paintCaptor.capture());

            boolean sawRedFamily = paintCaptor.getAllValues().stream()
                    .filter(p -> p instanceof Color)
                    .map(p -> (Color) p)
                    .anyMatch(c -> c.getRed() > c.getGreen() && c.getRed() > c.getBlue());
            assertTrue(sawRedFamily,
                    "expected a red-dominant paint for <red>; got "
                            + paintCaptor.getAllValues());
        } finally {
            spy.dispose();
        }
    }

    // ---- Parametric tags --------------------------------------------
    //
    // Parametric tags (registered by createDefaultParametricStyleMap)
    // take parameters after the tag name and produce a TextStyle from
    // a factory. These tests verify the registration is intact and
    // that the tag effects show up in the rendered output. Effects
    // observable through the Graphics2D spy:
    //   - <color ...>  → setPaint(Color)
    //   - <size N>     → setFont(Font with size related to N)
    //   - <family X>   → setFont(Font with family X)

    @Test
    void defaultParametricTagsAreRegistered() {
        MarkupRenderer r = new MarkupRenderer();
        assertNotNull(r.getParametricStyleForTag("color"));
        assertNotNull(r.getParametricStyleForTag("colour"));
        assertNotNull(r.getParametricStyleForTag("bgcolor"));
        assertNotNull(r.getParametricStyleForTag("bgcolour"));
        assertNotNull(r.getParametricStyleForTag("size"));
        assertNotNull(r.getParametricStyleForTag("family"));
        assertNotNull(r.getParametricStyleForTag("tracking"));
        assertNotNull(r.getParametricStyleForTag("width"));
        assertNotNull(r.getParametricStyleForTag("weight"));
    }

    @Test
    void getParametricStyleForUnknownTagReturnsNull() {
        MarkupRenderer r = new MarkupRenderer();
        assertNull(r.getParametricStyleForTag("no-such-tag"));
    }

    @Test
    void colorTagAppliesForegroundColor() {
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText("Hello <color FF0000>red</color> world.");

        Graphics2D spy = spyGraphics(400, 200);
        try {
            r.draw(spy, new Rectangle2D.Double(0, 0, 300, 100));

            ArgumentCaptor<Paint> paintCaptor = ArgumentCaptor.forClass(Paint.class);
            verify(spy, atLeast(1)).setPaint(paintCaptor.capture());

            boolean sawRed = paintCaptor.getAllValues().stream()
                    .filter(p -> p instanceof Color)
                    .map(p -> (Color) p)
                    .anyMatch(c -> c.getRed() == 255 && c.getGreen() == 0 && c.getBlue() == 0);
            assertTrue(sawRed,
                    "expected setPaint(Color(255,0,0)) for <color FF0000>; got "
                            + paintCaptor.getAllValues());
        } finally {
            spy.dispose();
        }
    }

    @Test
    void colourTagIsAliasForColorTag() {
        // British and American spellings should both work and produce
        // the same effect.
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText("<colour 00FF00>green</colour>");

        Graphics2D spy = spyGraphics(400, 200);
        try {
            r.draw(spy, new Rectangle2D.Double(0, 0, 300, 100));

            ArgumentCaptor<Paint> paintCaptor = ArgumentCaptor.forClass(Paint.class);
            verify(spy, atLeast(1)).setPaint(paintCaptor.capture());

            boolean sawGreen = paintCaptor.getAllValues().stream()
                    .filter(p -> p instanceof Color)
                    .map(p -> (Color) p)
                    .anyMatch(c -> c.getRed() == 0 && c.getGreen() == 255 && c.getBlue() == 0);
            assertTrue(sawGreen,
                    "expected setPaint(Color(0,255,0)) for <colour 00FF00>");
        } finally {
            spy.dispose();
        }
    }

    @Test
    void sizeTagChangesFontSize() {
        // Default size is 9pt. Size 24 produces a clearly larger
        // font; verify a non-default size appears in the rendered
        // glyph vectors.
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText("normal <size 24>large</size> normal");

        Graphics2D spy = spyGraphics(400, 200);
        try {
            r.draw(spy, new Rectangle2D.Double(0, 0, 300, 100));

            ArgumentCaptor<GlyphVector> gv = ArgumentCaptor.forClass(GlyphVector.class);
            verify(spy, atLeast(1)).drawGlyphVector(gv.capture(), anyFloat(), anyFloat());

            // Default style is Serif 9pt (set in the constructor).
            // Size 24 should produce a noticeably larger glyph-vector
            // font.
            boolean sawLarger = gv.getAllValues().stream()
                    .map(GlyphVector::getFont)
                    .anyMatch(f -> f.getSize2D() > 12f);
            assertTrue(sawLarger,
                    "expected at least one glyph vector with size > 12pt; got sizes "
                            + gv.getAllValues().stream()
                                    .map(GlyphVector::getFont)
                                    .map(Font::getSize2D)
                                    .toList());
        } finally {
            spy.dispose();
        }
    }

    @Test
    void familyTagChangesFontFamily() {
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText("normal <family Monospaced>mono</family> normal");

        Graphics2D spy = spyGraphics(400, 200);
        try {
            r.draw(spy, new Rectangle2D.Double(0, 0, 300, 100));

            ArgumentCaptor<GlyphVector> gv = ArgumentCaptor.forClass(GlyphVector.class);
            verify(spy, atLeast(1)).drawGlyphVector(gv.capture(), anyFloat(), anyFloat());

            boolean sawMono = gv.getAllValues().stream()
                    .map(GlyphVector::getFont)
                    .map(Font::getFamily)
                    .anyMatch("Monospaced"::equals);
            assertTrue(sawMono,
                    "expected at least one glyph vector with Monospaced family; got "
                            + gv.getAllValues().stream()
                                    .map(GlyphVector::getFont)
                                    .map(Font::getFamily)
                                    .toList());
        } finally {
            spy.dispose();
        }
    }

    @Test
    void invalidColorTagRendersContentInDefaultColor() {
        // The factory returns null for unparseable parameters; the
        // content renders without a color change.
        String markup = "Hello <color not-a-color>foo</color> world.";
        assertEquals(totalGlyphsRendered("Hello foo world."), totalGlyphsRendered(markup));

        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText(markup);
        Graphics2D spy = spyGraphics(400, 200);
        try {
            r.draw(spy, new Rectangle2D.Double(0, 0, 300, 100));
            ArgumentCaptor<Paint> paints = ArgumentCaptor.forClass(Paint.class);
            verify(spy, atLeast(0)).setPaint(paints.capture());
            assertTrue(paints.getAllValues().stream().allMatch(Color.BLACK::equals),
                    "expected only the default paint; got " + paints.getAllValues());
        } finally {
            spy.dispose();
        }
    }

    @Test
    void invalidSizeTagFallsBackToFactoryDefault() {
        // FontSizeStyleFactory substitutes its own 10pt default rather
        // than leaving the renderer's 9pt default in place.
        String markup = "Hello <size garbage>foo</size> world.";
        assertEquals(totalGlyphsRendered("Hello foo world."), totalGlyphsRendered(markup));
        List<Font> fonts = fontsRendered(markup);
        assertEquals(List.of(9f, 10f, 9f), fonts.stream().map(Font::getSize2D).toList());
    }

    @Test
    void nestedParametricTagsCompose() {
        // <color RED><size 20>foo</size></color> — both effects should
        // apply to the inner content.
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText("<color 0000FF><size 20>both</size></color>");

        Graphics2D spy = spyGraphics(400, 200);
        try {
            r.draw(spy, new Rectangle2D.Double(0, 0, 300, 100));

            ArgumentCaptor<Paint> paintCaptor = ArgumentCaptor.forClass(Paint.class);
            verify(spy, atLeast(1)).setPaint(paintCaptor.capture());

            boolean sawBlue = paintCaptor.getAllValues().stream()
                    .filter(p -> p instanceof Color)
                    .map(p -> (Color) p)
                    .anyMatch(c -> c.getRed() == 0 && c.getGreen() == 0 && c.getBlue() == 255);
            assertTrue(sawBlue, "expected blue paint from outer <color> tag");

            ArgumentCaptor<GlyphVector> gv = ArgumentCaptor.forClass(GlyphVector.class);
            verify(spy, atLeast(1)).drawGlyphVector(gv.capture(), anyFloat(), anyFloat());
            boolean sawLarger = gv.getAllValues().stream()
                    .map(GlyphVector::getFont)
                    .anyMatch(f -> f.getSize2D() > 12f);
            assertTrue(sawLarger, "expected larger font from inner <size> tag");
        } finally {
            spy.dispose();
        }
    }

    // ---- Headlines ---------------------------------------------------
    //
    // <h1>..<h7> split paragraphs in breakIntoParagraphs. A paragraph
    // is a title line only if it holds a tag with a registered style,
    // and only h1 has one by default.

    @Test
    void headlineStyleAppliesSize() {
        Font f = fontsRendered("<h1>Head</h1>").get(0);
        assertEquals(14f, f.getSize2D(), 0.01f);
    }

    @Test
    void headlineTagIsCaseInsensitive() {
        assertEquals(titleLines("<h1>Head</h1>\nBody"), titleLines("<H1>Head</H1>\nBody"));
        assertTrue(titleLines("<H1>Head").get(0));
    }

    @Test
    void textBeforeHeadlineBecomesItsOwnParagraph() {
        assertEquals("before\nHead", parsedText("before<h1>Head"));
    }

    @Test
    void newlineAfterHeadlineCloseIsSuppressed() {
        assertEquals("Head\nBody", parsedText("<h1>Head</h1>\nBody"));
    }

    @Test
    void onlyFirstNewlineAfterHeadlineCloseIsSuppressed() {
        assertEquals("Head\n \nBody", parsedText("<h1>Head</h1>\n\nBody"));
    }

    @Test
    void brAfterHeadlineCloseIsSuppressed() {
        assertEquals("Head\nBody", parsedText("<h1>Head</h1><br>Body"));
    }

    @Test
    void adjacentHeadlinesAreSeparateParagraphs() {
        assertEquals("One\nTwo\n ", parsedText("<h1>One</h1><h1>Two</h1>"));
    }

    @Test
    void textFollowingHeadlineCloseStaysInNextParagraph() {
        assertEquals("Head\nBody\nmore", parsedText("<h1>Head</h1>Body\nmore"));
    }

    @Test
    void headlinesWithoutDefaultStyleSplitButAreNotTitles() {
        for (int n = 2; n <= 7; ++n) {
            String markup = "before<h" + n + ">Head</h" + n + ">\nBody";
            assertEquals("before\nHead\nBody", parsedText(markup), markup);
            assertEquals(List.of(false, false, false), titleLines(markup), markup);
        }
    }

    @Test
    void addingStyleMakesAnyHeadlineLevelATitle() {
        for (int n = 2; n <= 7; ++n) {
            MarkupRenderer r = new MarkupRenderer();
            r.setStyleForTag("h" + n, r.getStyleForTag("h1"));
            assertTrue(titleLines(r, "<h" + n + ">Head").get(0), "h" + n);
        }
    }

    @Test
    void h0AndH8AreNotHeadlines() {
        for (String tag : List.of("h0", "h8")) {
            MarkupRenderer r = new MarkupRenderer();
            r.setStyleForTag(tag, r.getStyleForTag("h1"));
            String markup = "before<" + tag + ">Head";
            assertEquals(List.of(false), titleLines(r, markup), tag);
        }
    }

    @Test
    void removingH1StyleKeepsSplitButNotTitle() {
        MarkupRenderer r = new MarkupRenderer();
        r.removeTag("h1");
        assertEquals(List.of(false, false), titleLines(r, "before<h1>Head"));
    }

    @Test
    void closeTagAtEndOfMarkupLeavesTrailingEmptyParagraph() {
        // The close tag is carried into a new paragraph, which then
        // adds a padded blank line to the measured height.
        assertEquals("Head\n ", parsedText("<h1>Head</h1>"));
        MarkupRenderer r = new MarkupRenderer();
        try (GraphicsHandle gh = new GraphicsHandle(400, 300)) {
            Rectangle2D region = new Rectangle2D.Double(0, 0, 300, 200);
            r.setMarkupText("<h1>Head");
            double open = r.measure(gh.g, region);
            r.setMarkupText("<h1>Head</h1>");
            assertTrue(r.measure(gh.g, region) > open);
        }
    }

    @Test
    void paragraphAfterHeadlineIsNotATitle() {
        assertEquals(List.of(true, false, false), titleLines("<h1>Head</h1>\nBody\nmore"));
    }

    @Test
    void paragraphAfterHeadlineUsesBaseAlignment() {
        List<LayoutSegment> segments = headlineLayout("<h1>Head</h1>\nBody",
                MarkupRenderer.LAYOUT_RIGHT, LEFT_TOP);
        assertTrue(paragraphX(segments, 0) > 0f);
        assertEquals(0f, paragraphX(segments, 1), 0.01f);
    }

    @Test
    void headlineAlignmentDefaultsToCenter() {
        assertEquals(MarkupRenderer.LAYOUT_CENTER, new MarkupRenderer().getHeadlineAlignment());
    }

    @Test
    void setHeadlineAlignmentKeepsOnlyHorizontalBits() {
        MarkupRenderer r = new MarkupRenderer();
        r.setHeadlineAlignment(MarkupRenderer.LAYOUT_RIGHT | MarkupRenderer.LAYOUT_BOTTOM
                | MarkupRenderer.LAYOUT_JUSTIFY);
        assertEquals(MarkupRenderer.LAYOUT_RIGHT, r.getHeadlineAlignment());
    }

    @Test
    void setHeadlineAlignmentWithoutHorizontalBitsUsesLeft() {
        MarkupRenderer r = new MarkupRenderer();
        r.setHeadlineAlignment(MarkupRenderer.LAYOUT_BOTTOM);
        assertEquals(MarkupRenderer.LAYOUT_LEFT, r.getHeadlineAlignment());
    }

    @Test
    void headlineUsesHeadlineAlignmentNotBaseAlignment() {
        float left = firstHeadlineX(MarkupRenderer.LAYOUT_LEFT,
                MarkupRenderer.LAYOUT_RIGHT | MarkupRenderer.LAYOUT_TOP);
        float center = firstHeadlineX(MarkupRenderer.LAYOUT_CENTER, LEFT_TOP);
        float right = firstHeadlineX(MarkupRenderer.LAYOUT_RIGHT, LEFT_TOP);
        assertEquals(0f, left, 0.01f);
        assertTrue(center > 0f);
        assertEquals(right / 2f, center, 0.01f);
    }

    @Test
    void inlineAlignmentTagDoesNotOverrideHeadlineAlignment() {
        List<LayoutSegment> segments = headlineLayout("<h1><right>Head",
                MarkupRenderer.LAYOUT_LEFT, LEFT_TOP);
        assertEquals(0f, segments.get(0).getXOffset(), 0.01f);
    }

    @Test
    void changingHeadlineAlignmentAppliesToNextLayout() {
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText("<h1>Head");
        Rectangle2D region = new Rectangle2D.Double(0, 0, 100, 50);
        List<LayoutSegment> before = new ArrayList<>();
        List<LayoutSegment> after = new ArrayList<>();
        try (GraphicsHandle gh = new GraphicsHandle(200, 150)) {
            r.setHeadlineAlignment(MarkupRenderer.LAYOUT_LEFT);
            r.appendSegments(gh.g, region, before);
            r.setHeadlineAlignment(MarkupRenderer.LAYOUT_RIGHT);
            r.appendSegments(gh.g, region, after);
        }
        assertTrue(after.get(0).getXOffset() > before.get(0).getXOffset());
    }

    @Test
    void headlineAddsSpaceAboveAndBelowComparedToEquivalentStyle() {
        MarkupRenderer r = new MarkupRenderer();
        r.setStyleForTag("big", r.getStyleForTag("h1"));
        List<LayoutSegment> plain = segmentsOf(r, "<big>Head</big>\nBody\nmore");
        List<LayoutSegment> title = segmentsOf(r, "<h1>Head</h1>\nBody\nmore");
        assertTrue(title.get(0).getYOffset() > plain.get(0).getYOffset());
        assertTrue(title.get(1).getYOffset() - title.get(0).getYOffset()
                > plain.get(1).getYOffset() - plain.get(0).getYOffset());
    }

    private static List<Boolean> titleLines(String markup) {
        return titleLines(new MarkupRenderer(), markup);
    }

    private static List<Boolean> titleLines(MarkupRenderer r, String markup) {
        segmentsOf(r, markup);
        List<Boolean> titles = new ArrayList<>();
        for (MarkupRenderer.StyledParagraph p : r.getStyledText()) {
            titles.add(p.isTitleLine());
        }
        return titles;
    }

    private static List<LayoutSegment> segmentsOf(MarkupRenderer r, String markup) {
        r.setMarkupText(markup);
        List<LayoutSegment> segments = new ArrayList<>();
        try (GraphicsHandle gh = new GraphicsHandle(400, 300)) {
            r.appendSegments(gh.g, new Rectangle2D.Double(0, 0, 300, 200), segments);
        }
        return segments;
    }

    private static List<LayoutSegment> headlineLayout(String markup, int headlineAlign, int align) {
        MarkupRenderer r = new MarkupRenderer();
        r.setHeadlineAlignment(headlineAlign);
        r.setAlignment(align);
        return segmentsOf(r, markup);
    }

    private static float firstHeadlineX(int headlineAlign, int align) {
        return headlineLayout("<h1>Head", headlineAlign, align).get(0).getXOffset();
    }

    // ---- Tabs --------------------------------------------------------
    //
    // <tabwidth> calls setTabWidths while parsing, so it applies to the
    // whole block and stays set on the renderer. The no-arg constructor
    // uses 72 dpi, so a 1in stop is 72 units wide.

    @Test
    void tabWidthDefaultsToOneCentimeter() {
        MarkupRenderer r = new MarkupRenderer();
        assertEquals(1d / 2.54d, r.getTabWidth(), 1e-9);
        assertArrayEquals(new double[]{1d / 2.54d}, r.getTabWidths(), 1e-9);
    }

    @Test
    void setTabWidthReplacesMultipleStops() {
        MarkupRenderer r = new MarkupRenderer();
        r.setTabWidths(new double[]{1d, 2d});
        r.setTabWidth(3d);
        assertArrayEquals(new double[]{3d}, r.getTabWidths());
    }

    @Test
    void setTabWidthsRejectsNullAndEmpty() {
        MarkupRenderer r = new MarkupRenderer();
        assertThrows(NullPointerException.class, () -> r.setTabWidths(null));
        assertThrows(IllegalArgumentException.class, () -> r.setTabWidths(new double[0]));
    }

    @Test
    void tabWidthArraysAreCopied() {
        MarkupRenderer r = new MarkupRenderer();
        double[] stops = {1d, 2d};
        r.setTabWidths(stops);
        stops[0] = 9d;
        r.getTabWidths()[1] = 9d;
        assertArrayEquals(new double[]{1d, 2d}, r.getTabWidths());
    }

    @Test
    void tabsAdvanceToEvenlySpacedStops() {
        assertEquals(List.of(0f, 72f, 144f), tabXs("<tabwidth 1in>a\tb\tc"));
    }

    @Test
    void tabStopsScaleWithDpi() {
        MarkupRenderer r = new MarkupRenderer(144d);
        r.setAlignment(LEFT_TOP);
        r.setTabWidth(1d);
        assertEquals(List.of(0f, 144f), xOffsets(segmentsOf(r, "a\tb")));
    }

    @Test
    void tabSkipsStopsAlreadyPassed() {
        MarkupRenderer r = new MarkupRenderer();
        r.setTabWidth(0.1d);
        float textEnd = xOffsets(segmentsOf(r, "a long enough run\tb")).get(1);
        assertTrue(textEnd > 7.2f);
        assertEquals(0f, textEnd % 7.2f, 0.01f);
    }

    @Test
    void multipleStopsCycleThroughWidths() {
        assertEquals(List.of(0f, 72f, 216f, 288f), tabXs("<tabwidth 1in 2in>a\tb\tc\td"));
    }

    @Test
    void tabStopsAreRelativeToEachParagraph() {
        List<LayoutSegment> segments = layoutOf("<tabwidth 1in>a\tb\nc\td", 300, 200);
        assertEquals(List.of(0f, 72f, 0f, 72f), xOffsets(segments));
    }

    @Test
    void alignmentShiftsTabbedSegmentsTogether() {
        List<Float> xs = xOffsets(layoutAligned("<tabwidth 1in>a\tb",
                MarkupRenderer.LAYOUT_RIGHT | MarkupRenderer.LAYOUT_TOP, 300, 200));
        assertTrue(xs.get(0) > 0f);
        assertEquals(72f, xs.get(1) - xs.get(0), 0.01f);
    }

    @Test
    void lastTabwidthTagApplies() {
        assertEquals(List.of(0f, 144f), tabXs("<tabwidth 1in>a\t<tabwidth 2in>b"));
    }

    @Test
    void tabwidthTagStaysSetForLaterMarkup() {
        MarkupRenderer r = new MarkupRenderer();
        segmentsOf(r, "<tabwidth 1in>a");
        assertEquals(1d, r.getTabWidth(), 1e-9);
        assertEquals(List.of(0f, 72f), xOffsets(segmentsOf(r, "a\tb")));
    }

    @Test
    void tabwidthTagIsCaseInsensitive() {
        assertEquals(tabXs("<tabwidth 1in>a\tb"), tabXs("<TABWIDTH 1in>a\tb"));
    }

    @Test
    void tabwidthTagWithoutParametersKeepsWidth() {
        MarkupRenderer r = new MarkupRenderer();
        r.setTabWidth(2d);
        segmentsOf(r, "<tabwidth>a");
        assertArrayEquals(new double[]{2d}, r.getTabWidths());
    }

    @Test
    void invalidTabwidthParametersBecomeZero() {
        MarkupRenderer r = new MarkupRenderer();
        segmentsOf(r, "<tabwidth 1in garbage -1in NaN>a");
        assertArrayEquals(new double[]{1d, 0d, 0d, 0d}, r.getTabWidths());
    }

    @Test
    void changingTabWidthAppliesToNextLayout() {
        MarkupRenderer r = new MarkupRenderer();
        r.setTabWidth(1d);
        assertEquals(List.of(0f, 72f), xOffsets(segmentsOf(r, "a\tb")));
        r.setTabWidth(2d);
        assertEquals(List.of(0f, 144f), xOffsets(segmentsOf(r, "a\tb")));
    }

    @Test
    void leadingTabStaysAtMarginWithSingleWidth() {
        // The single-width stop array starts at the margin; the
        // multi-width array starts one width in.
        List<Float> single = tabXs("<tabwidth 1in>\tb");
        assertEquals(0f, single.get(single.size() - 1), 0.01f);
        List<Float> multi = tabXs("<tabwidth 1in 1in>\tb");
        assertEquals(72f, multi.get(multi.size() - 1), 0.01f);
    }

    @Test
    void textPastLastStopPreservesAllCharacters() {
        List<LayoutSegment> segments = layoutOf("<tabwidth 1in>a\tb\tc\td\te", 100, 200);
        assertEquals(9, totalCharacters(segments));
    }

    @Test
    void singleLineDrawIgnoresTabStops() {
        MarkupRenderer r = new MarkupRenderer();
        r.setTabWidth(2d);
        r.setMarkupText("a\tb");
        try (GraphicsHandle gh = new GraphicsHandle(400, 200)) {
            double width = r.drawAsSingleLine(gh.g, new Rectangle2D.Double(0, 0, 300, 100));
            assertTrue(width > 0d && width < 144d, "width " + width);
        }
    }

    @Test
    void justificationSkipsSegmentEndingAtTab() {
        String body = "one two\tThe quick brown fox jumps over the lazy dog again.";
        float plain = textLayoutOf(layoutAligned(body, LEFT_TOP, 100, 400).get(0)).getAdvance();
        float justified = textLayoutOf(
                layoutAligned("<justified>" + body, LEFT_TOP, 100, 400).get(0)).getAdvance();
        assertEquals(plain, justified, 0.01f);
    }

    @Test
    void justifiedSegmentAfterTabOverrunsRightMargin() {
        // getJustifiedLayout gets the full line width, not what remains
        // after the tab stop.
        List<LayoutSegment> segments = layoutAligned(
                "<justified><tabwidth 0.5in>a\tThe quick brown fox jumps over the lazy dog.",
                LEFT_TOP, 100, 400);
        LayoutSegment afterTab = segments.get(1);
        assertTrue(afterTab.getXOffset() + textLayoutOf(afterTab).getVisibleAdvance() > 100f);
    }

    // ---- Measurements ------------------------------------------------

    @Test
    void parseMeasurementConvertsUnitsToInches() {
        assertEquals(2d, MarkupRenderer.parseMeasurement("2in"), 1e-9);
        assertEquals(1d, MarkupRenderer.parseMeasurement("72pt"), 1e-9);
        assertEquals(1d, MarkupRenderer.parseMeasurement("2.54cm"), 1e-9);
    }

    @Test
    void parseMeasurementDefaultsToCentimeters() {
        assertEquals(1d, MarkupRenderer.parseMeasurement("2.54"), 1e-9);
    }

    @Test
    void parseMeasurementUnitIsCaseInsensitive() {
        assertEquals(2d, MarkupRenderer.parseMeasurement("2IN"), 1e-9);
    }

    @Test
    void parseMeasurementReturnsNegativeForInvalidInput() {
        for (String bad : List.of("", "in", "garbage", "1 in x")) {
            assertTrue(MarkupRenderer.parseMeasurement(bad) < 0d, bad);
        }
    }

    @Test
    void parseMeasurementRejectsNaNAndInfinity() {
        // Lowercasing the input stops Double.parseDouble recognizing them.
        assertTrue(MarkupRenderer.parseMeasurement("NaN") < 0d);
        assertTrue(MarkupRenderer.parseMeasurement("Infinity") < 0d);
    }

    @Test
    void unusableTabWidthsMakeTabsZeroWidthSpaces() {
        double[][] unusable = {{0d}, {-1d}, {Double.NaN}, {1e-12d}, {0d, 0d}, {-1d, -1d}, {1d, -1d}};
        for (double[] widths : unusable) {
            MarkupRenderer r = new MarkupRenderer();
            r.setTabWidths(widths);
            List<LayoutSegment> segments = segmentsOf(r, "a\tb");
            assertEquals(2, segments.size(), () -> java.util.Arrays.toString(widths));
            assertEquals(textLayoutOf(segments.get(0)).getAdvance(),
                    segments.get(1).getXOffset(), 0.01f);
        }
    }

    @Test
    void invalidTabwidthTagDoesNotBreakLayout() {
        assertEquals(2, tabXs("<tabwidth garbage>a\tb").size());
        assertEquals(1, tabXs("<tabwidth garbage garbage>a").size());
    }

    @Test
    void onePointIsSmallestUsableTabCycle() {
        MarkupRenderer r = new MarkupRenderer();
        r.setTabWidth(1d / 72d);
        float x = xOffsets(segmentsOf(r, "a\tb")).get(1);
        assertEquals(Math.rint(x), x, 0.01f);
    }

    @Test
    void zeroWidthInUsableCycleRepeatsPreviousStop() {
        assertEquals(List.of(0f, 72f, 144f), tabXs("<tabwidth 1in 0>a\tb\tc"));
    }

    private static List<Float> tabXs(String markup) {
        return xOffsets(layoutOf(markup, 300, 200));
    }

    private static List<Float> xOffsets(List<LayoutSegment> segments) {
        return segments.stream().map(LayoutSegment::getXOffset).toList();
    }

    // ---- Macro parameters --------------------------------------------

    @Test
    void macroSubstitutesParametersInOrder() {
        assertEquals("b+a", parsedText("<define swap \"@2;+@1;\"><swap a b>"));
    }

    @Test
    void macroParameterCanRepeatAndExtrasAreIgnored() {
        assertEquals("aa", parsedText("<define twice @1;@1;><twice a b>"));
    }

    @Test
    void macroPreservesParameterCase() {
        assertEquals("MixedCase", parsedText("<define echo @1;><ECHO MixedCase>"));
    }

    @Test
    void missingOrInvalidMacroParameterShowsPlaceholder() {
        assertEquals("?2?", parsedText("<define m @2;><m a>"));
        assertEquals("?0?", parsedText("<define m @0;><m a>"));
        assertEquals("?-1?", parsedText("<define m @-1;><m a>"));
        assertEquals("?x?", parsedText("<define m @x;><m a>"));
        assertEquals("??", parsedText("<define m @;><m a>"));
    }

    @Test
    void definitionUsedWithoutParametersShowsPlaceholder() {
        assertEquals("?1?", parsedText("<define m @1;><m>"));
    }

    @Test
    void unterminatedMacroReferenceDropsRestOfDefinition() {
        assertEquals("a", parsedText("<define mail a@b.com><mail>"));
    }

    @Test
    void exactNameMatchWinsOverParametricMatch() {
        assertEquals("exact,parametric",
                parsedText("<define \"m x\" exact><define m parametric><m x>,<m y>"));
    }

    @Test
    void definitionsAreClearedAfterEachParse() {
        MarkupRenderer r = new MarkupRenderer();
        assertEquals("x", parsedText(r, "<define d x><d>"));
        assertEquals(" ", parsedText(r, "<d>"));
    }

    @Test
    void macroExpansionIsReparsed() {
        List<Font> fonts = fontsRendered("<define bold \"<b>@1;</b>\"><bold x>");
        assertTrue(fonts.get(0).isBold());
    }

    @Test
    void definitionShadowsBuiltInStyle() {
        assertEquals("x", parsedText("<define b x><b>"));
    }

    @Test
    void definitionTakesEffectOnlyAfterItsTag() {
        assertEquals("x", parsedText("<later><define later x><later>"));
    }

    @Test
    void definitionWinsOverReplacementOfSameName() {
        MarkupRenderer r = new MarkupRenderer();
        r.setReplacementForTag("name", "replacement");
        assertEquals("definition,", parsedText(r, "<define name definition><name>,"));
        assertEquals("replacement,", parsedText(r, "<name>,"));
    }

    @Test
    void replacementTagsDoNotExpandMacroParameters() {
        MarkupRenderer r = new MarkupRenderer();
        r.setReplacementForTag("name", "x@1;");
        assertEquals("x@1;", parsedText(r, "<name>"));
    }

    @Test
    void tagsBeginningWithDefineAreTreatedAsDefine() {
        // The handler tests startsWith, so <defined> also defines.
        assertEquals("y", parsedText("<defined x y><x>"));
    }

    @Test
    void userStyleCannotOverrideDefineTag() {
        // The define handler runs before the style table is consulted.
        MarkupRenderer r = new MarkupRenderer();
        r.setStyleForTag("define", TextStyle.BOLD_STYLE);
        assertFalse(fontsRendered(r, "<define>x</define>").get(0).isBold());
    }

    @Test
    void defineUsesOnlyFirstContentParameter() {
        assertEquals("Hello", parsedText("<define greeting Hello world><greeting>"));
    }

    @Test
    void quotedDefineKeepsMultipleWords() {
        assertEquals("Hello world", parsedText("<define greeting \"Hello world\"><greeting>"));
    }

    @Test
    void repeatKeySubstitutesItsValue() {
        MarkupRenderer r = new MarkupRenderer();
        assertEquals("7-a", r.replaceMacroParameters("@i;-@1;", new String[]{"a"}, "i", "7"));
    }

    // ---- Repeat ------------------------------------------------------

    @Test
    void repeatExpandsContent() {
        assertEquals("ababab", parsedText("<repeat 3 ab>"));
    }

    @Test
    void repeatTagIsCaseInsensitiveAndKeepsContentCase() {
        assertEquals("AbAb", parsedText("<REPEAT 2 Ab>"));
    }

    @Test
    void repeatContentIsReparsed() {
        List<Font> fonts = fontsRendered("<repeat 2 \"<b>x</b>\">");
        assertTrue(fonts.get(0).isBold());
        assertEquals("xx", parsedText("<repeat 2 \"<b>x</b>\">"));
    }

    @Test
    void nestedRepeatMultiplies() {
        assertEquals("aaaa", parsedText("<repeat 2 \"<repeat 2 a>\">"));
    }

    @Test
    void repeatAcceptsLimitAndDropsCountsOutsideRange() {
        assertEquals(10_000, parsedText("<repeat 10000 a>").length());
        for (String count : List.of("0", "-1", "10001", "many")) {
            assertEquals("xy", parsedText("x<repeat " + count + " a>y"), count);
        }
    }

    @Test
    void repeatWithWrongParameterCountIsDropped() {
        assertEquals("xy", parsedText("x<repeat>y"));
        assertEquals("xy", parsedText("x<repeat 2>y"));
        assertEquals("xy", parsedText("x<repeat 2 a b>y"));
    }

    // ---- Expansion limit ---------------------------------------------

    @Test
    void expansionLimitRoundTrips() {
        MarkupRenderer r = new MarkupRenderer();
        assertEquals(MarkupRenderer.getDefaultExpansionLimit(), r.getExpansionLimit());
        r.setExpansionLimit(10);
        assertEquals(10, r.getExpansionLimit());
    }

    @Test
    void defaultExpansionLimitRejectsValuesBelowOne() {
        assertThrows(IllegalArgumentException.class, () -> MarkupRenderer.setDefaultExpansionLimit(0));
    }

    private static final String OVERFLOW_MESSAGE = "Probable infinite recursion";

    @Test
    void selfReferencingDefinitionShowsOverflowMessage() {
        assertTrue(parsedText("<define loop \"<loop>\"><loop>").startsWith(OVERFLOW_MESSAGE));
    }

    @Test
    void mutuallyRecursiveDefinitionsShowOverflowMessage() {
        String text = parsedText("<define a \"<bb>\"><define bb \"<a>\"><a>");
        assertTrue(text.startsWith(OVERFLOW_MESSAGE), text);
    }

    @Test
    void growingDefinitionShowsOverflowMessage() {
        MarkupRenderer r = new MarkupRenderer();
        r.setExpansionLimit(100);
        String text = parsedText(r, "<define grow \"x<grow>\"><grow>");
        assertTrue(text.startsWith(OVERFLOW_MESSAGE), text);
    }

    @Test
    void oversizedRepeatShowsOverflowMessage() {
        MarkupRenderer r = new MarkupRenderer();
        r.setExpansionLimit(5);
        String text = parsedText(r, "<repeat 100 a>");
        assertTrue(text.startsWith(OVERFLOW_MESSAGE), text);
    }

    @Test
    void overflowInShortLineDoesNotFailBuildingMessage() {
        MarkupRenderer r = new MarkupRenderer();
        r.setExpansionLimit(5);
        String text = parsedText(r, "<repeat 10 abc>");
        assertTrue(text.startsWith(OVERFLOW_MESSAGE), text);
    }

    @Test
    void overflowOnlyReplacesItsOwnParagraph() {
        String[] lines = parsedText("before\n<define loop \"<loop>\"><loop>\nafter").split("\n");
        assertEquals(3, lines.length);
        assertEquals("before", lines[0]);
        assertTrue(lines[1].startsWith(OVERFLOW_MESSAGE));
        assertEquals("after", lines[2]);
    }

    @Test
    void overflowDiscardsTabsAndStylesFromFailedParagraph() {
        String markup = "a\t<b>b\t<define loop \"<loop>\"><loop>\nafter</b>";
        List<LayoutSegment> segments = layoutOf(markup, 2000, 200);
        assertEquals(1, segments.stream().filter(s -> s.getSourceParagraphIndex() == 0).count());
        assertEquals(1, segments.stream().filter(s -> s.getSourceParagraphIndex() == 1).count());
        List<Font> fonts = fontsRendered(markup);
        assertTrue(fonts.get(fonts.size() - 1).isBold(), "open style carries into next paragraph");
    }

    @Test
    void manyLegitimateExpansionsAreAllowed() {
        assertEquals("\u2014".repeat(5_000), parsedText("<repeat 5000 \"<emdash>\">"));
    }

    // ---- Tag parameters ----------------------------------------------

    @Test
    void parametersSplitOnWhitespace() {
        assertEquals(List.of("a", "b", "c"), tagParameters("<p a  b\tc>"));
    }

    @Test
    void tagWithoutParametersHasNone() {
        assertEquals(List.of(), tagParameters("<p>"));
    }

    @Test
    void parametersPreserveCase() {
        assertEquals(List.of("MixedCase"), tagParameters("<P MixedCase>"));
    }

    @Test
    void quotedParameterKeepsWhitespaceAndAngleBrackets() {
        assertEquals(List.of("a b", "c>d", "e"), tagParameters("<p \"a b\" \"c>d\" e>"));
    }

    @Test
    void emptyQuotedParameterIsKept() {
        assertEquals(List.of("", "a"), tagParameters("<p \"\" a>"));
    }

    @Test
    void quotedParameterEscapes() {
        assertEquals(List.of("a\"b"), tagParameters("<p \"a\\\"b\">"));
        assertEquals(List.of("a\\b"), tagParameters("<p \"a\\\\b\">"));
        assertEquals(List.of("a\\nb"), tagParameters("<p \"a\\nb\">"));
    }

    @Test
    void escapedQuoteDoesNotEndTagParsing() {
        assertEquals(List.of("a\">b"), tagParameters("<p \"a\\\">b\">"));
    }

    @Test
    void backslashIsLiteralOutsideQuotes() {
        assertEquals(List.of("a\\b"), tagParameters("<p a\\b>"));
    }

    @Test
    void closingQuoteEndsParameterWithoutWhitespace() {
        assertEquals(List.of("a", "b"), tagParameters("<p \"a\"b>"));
    }

    @Test
    void quoteInsideUnquotedParameterIsLiteral() {
        assertEquals(List.of("a\"b", "c\""), tagParameters("<p a\"b c\">"));
    }

    @Test
    void quotedNewlineAndBreakTagsDoNotSplitParagraph() {
        assertEquals(List.of("a\nb"), tagParameters("<p \"a\nb\">"));
        assertEquals(List.of("a<br>b"), tagParameters("<p \"a<br>b\">"));
        assertEquals(List.of("<h1>"), tagParameters("<p \"<h1>\">"));
        assertEquals("xy", parsedText("x<unknown \"a\nb<br>c<h1>\">y"));
    }

    @Test
    void escapedQuoteSurvivesParagraphSplitting() {
        assertEquals("xy\nz", parsedText("x<unknown \"a\\\"\nb\">y\nz"));
    }

    @Test
    void unterminatedQuoteDropsRestOfMarkup() {
        assertEquals("x", parsedText("x<p \"a>y\nz"));
    }

    private static List<String> tagParameters(String markup) {
        List<String> params = new ArrayList<>();
        MarkupRenderer r = new MarkupRenderer() {
            @Override
            protected String handleUnknownTag(String tagNameLower, String tagNameOriginal) {
                params.addAll(List.of(getTagParameters()));
                return null;
            }
        };
        parsedText(r, markup);
        return params;
    }

    // ---- Unicode escapes ---------------------------------------------

    @Test
    void unicodeEscapeInsertsCharacter() {
        assertEquals("a@b", parsedText("a<u+0040>b"));
        assertEquals("a@b", parsedText("a<U+0040>b"));
    }

    @Test
    void unicodeEscapeAcceptsFewerThanFourDigits() {
        assertEquals("A", parsedText("<u+41>"));
    }

    @Test
    void angleBracketEscapesProduceLiteralBrackets() {
        assertEquals("<", parsedText("<u+003C>"));
        assertEquals(">", parsedText("<u+003e>"));
    }

    @Test
    void angleBracketEscapesDoNotFormTags() {
        assertEquals("<b>x", parsedText("<u+003C>b<u+003E>x"));
        assertFalse(fontsRendered("<u+003C>b<u+003E>x").get(0).isBold());
    }

    @Test
    void unicodeEscapeOutputIsStillReparsedForPunctuation() {
        assertEquals("“x”", parsedText("<u+0022>x<u+0022>"));
    }

    @Test
    void invalidUnicodeEscapesAreDropped() {
        for (String tag : List.of("<u+>", "<u+zzzz>", "</u+0040>")) {
            assertEquals("xy", parsedText("x" + tag + "y"), tag);
        }
    }

    @Test
    void codepointsOutsideBmpAreNotSupported() {
        assertEquals("xy", parsedText("x<u+1F600>y"));
    }

    // ---- Removing tags -----------------------------------------------

    @Test
    void removedStyleTagIsTreatedAsUnknown() {
        MarkupRenderer r = new MarkupRenderer();
        r.removeTag("b");
        assertNull(r.getStyleForTag("b"));
        assertFalse(fontsRendered(r, "<b>x</b>").get(0).isBold());
    }

    @Test
    void removedParametricTagIsTreatedAsUnknown() {
        MarkupRenderer r = new MarkupRenderer();
        r.removeTag("size");
        assertNull(r.getParametricStyleForTag("size"));
        assertEquals(9f, fontsRendered(r, "<size 20>x</size>").get(0).getSize2D(), 0.01f);
    }

    @Test
    void removedReplacementTagIsTreatedAsUnknown() {
        MarkupRenderer r = new MarkupRenderer();
        r.removeTag("emdash");
        assertNull(r.getReplacementForTag("emdash"));
        assertEquals("xy", parsedText(r, "x<emdash>y"));
    }

    @Test
    void removingUnknownTagHasNoEffect() {
        MarkupRenderer r = new MarkupRenderer();
        r.removeTag("no-such-tag");
        assertNotNull(r.getStyleForTag("b"));
    }

    @Test
    void removeTagReparsesCurrentMarkup() {
        MarkupRenderer r = new MarkupRenderer();
        assertEquals("x—y", parsedText(r, "x<emdash>y"));
        r.removeTag("emdash");
        assertEquals("xy", parsedText(r, "x<emdash>y"));
    }

    @Test
    void removeTagIsCaseSensitive() {
        MarkupRenderer r = new MarkupRenderer();
        r.removeTag("B");
        assertNotNull(r.getStyleForTag("b"));
    }

    @Test
    void tagRegisteredWithUppercaseNameNeverMatches() {
        // Markup lookups use the lowercased tag name.
        MarkupRenderer r = new MarkupRenderer();
        r.setReplacementForTag("Name", "replacement");
        assertEquals("xy", parsedText(r, "x<Name>y"));
    }

    @Test
    void settingOneTagKindReplacesOtherKinds() {
        MarkupRenderer r = new MarkupRenderer();
        r.setReplacementForTag("b", "text");
        assertNull(r.getStyleForTag("b"));
        r.setParametricStyleForTag("b", FontSizeStyleFactory.getShared());
        assertNull(r.getReplacementForTag("b"));
        r.setStyleForTag("b", TextStyle.BOLD_STYLE);
        assertNull(r.getParametricStyleForTag("b"));
    }

    @Test
    void removeAllTagsClearsEveryKindOfTag() {
        MarkupRenderer r = new MarkupRenderer();
        r.removeAllTags();
        assertNull(r.getStyleForTag("b"));
        assertNull(r.getParametricStyleForTag("size"));
        assertNull(r.getReplacementForTag("emdash"));
        assertEquals("xy", parsedText(r, "x<b><size 20><emdash>y"));
    }

    @Test
    void removeAllTagsKeepsBuiltInParsing() {
        MarkupRenderer r = new MarkupRenderer();
        r.removeAllTags();
        assertEquals("a\nb<c@", parsedText(r, "a<br>b<lt>c<u+0040>"));
        assertEquals("x", parsedText(r, "<define d x><d>"));
        assertEquals("aa", parsedText(r, "<repeat 2 a>"));
    }

    // ---- Fitting limits ----------------------------------------------

    private static final String OVERFLOWING = repeat("Line ", 60);
    private static final Rectangle2D SMALL_REGION = new Rectangle2D.Double(0, 0, 100, 40);

    @Test
    void fittingLimitDefaults() {
        MarkupRenderer r = new MarkupRenderer();
        assertEquals(0.5d, r.getScalingLimit());
        assertEquals(1_000, r.getScalingFractionalLimit());
        assertEquals(0.5f, r.getTightnessLimit());
    }

    @Test
    void setScalingLimitRejectsValuesOutsideUnitRange() {
        MarkupRenderer r = new MarkupRenderer();
        assertThrows(IllegalArgumentException.class, () -> r.setScalingLimit(0d));
        assertThrows(IllegalArgumentException.class, () -> r.setScalingLimit(-0.5d));
        assertThrows(IllegalArgumentException.class, () -> r.setScalingLimit(1.1d));
        r.setScalingLimit(1d);
        assertEquals(1d, r.getScalingLimit());
    }

    @Test
    void setScalingFractionalLimitRejectsNonPositiveValues() {
        MarkupRenderer r = new MarkupRenderer();
        assertThrows(IllegalArgumentException.class, () -> r.setScalingFractionalLimit(0));
        r.setScalingFractionalLimit(10);
        assertEquals(10, r.getScalingFractionalLimit());
    }

    @Test
    void tightnessLimitAboveLineTightnessRaisesLineTightness() {
        MarkupRenderer r = new MarkupRenderer();
        r.setTightnessLimit(2f);
        assertEquals(2f, r.getTightnessLimit());
        assertEquals(2f, r.getLineTightness());
    }

    @Test
    void lineTightnessBelowLimitLowersLimit() {
        MarkupRenderer r = new MarkupRenderer();
        r.setLineTightness(0.25f);
        assertEquals(0.25f, r.getLineTightness());
        assertEquals(0.25f, r.getTightnessLimit());
    }

    @Test
    void scaleFitsOverflowingTextAboveLimit() {
        // Low enough for the text to fit at any platform's font metrics.
        MarkupRenderer r = fitted(MarkupRenderer.FIT_SCALE_TEXT);
        r.setScalingLimit(0.25d);
        double height = measure(r, SMALL_REGION);
        double scale = appliedScale(r);
        assertThat(height).as("height").isLessThanOrEqualTo(SMALL_REGION.getHeight());
        assertThat(scale).as("scale").isStrictlyBetween(0.25d, 1d);
    }

    @Test
    void textThatFitsIsNotScaled() {
        MarkupRenderer r = fitted(MarkupRenderer.FIT_BOTH);
        r.setMarkupText("Line");
        measure(r, SMALL_REGION);
        assertEquals(1d, appliedScale(r));
    }

    @Test
    void scalingLimitStopsScaleEvenIfTextOverflows() {
        MarkupRenderer r = fitted(MarkupRenderer.FIT_SCALE_TEXT);
        r.setScalingLimit(0.8d);
        double height = measure(r, SMALL_REGION);
        assertEquals(0.8d, appliedScale(r), 1e-9);
        assertTrue(height > SMALL_REGION.getHeight());
    }

    @Test
    void scalingLimitOfOneDisablesScaling() {
        MarkupRenderer r = fitted(MarkupRenderer.FIT_SCALE_TEXT);
        r.setScalingLimit(1d);
        measure(r, SMALL_REGION);
        assertEquals(1d, appliedScale(r));
    }

    @Test
    void scaleIsRoundedToFractionalLimit() {
        MarkupRenderer r = fitted(MarkupRenderer.FIT_SCALE_TEXT);
        r.setScalingFractionalLimit(10);
        measure(r, SMALL_REGION);
        double tenths = appliedScale(r) * 10d;
        assertEquals(Math.rint(tenths), tenths, 1e-9);
    }

    @Test
    void coarseFractionalLimitSelectsOnlyAvailableScale() {
        MarkupRenderer r = fitted(MarkupRenderer.FIT_SCALE_TEXT);
        r.setScalingFractionalLimit(2);
        measure(r, SMALL_REGION);
        assertEquals(0.5d, appliedScale(r));
    }

    @Test
    void fractionalLimitOfOneLeavesTextUnscaled() {
        MarkupRenderer r = fitted(MarkupRenderer.FIT_SCALE_TEXT);
        r.setScalingFractionalLimit(1);
        measure(r, SMALL_REGION);
        assertEquals(1d, appliedScale(r));
    }

    @Test
    void changingScalingLimitAppliesToNextLayout() {
        MarkupRenderer r = fitted(MarkupRenderer.FIT_SCALE_TEXT);
        r.setScalingLimit(0.8d);
        measure(r, SMALL_REGION);
        r.setScalingLimit(0.9d);
        measure(r, SMALL_REGION);
        assertEquals(0.9d, appliedScale(r), 1e-9);
    }

    @Test
    void tighteningStopsAtTightnessLimit() {
        MarkupRenderer limit = fitted(MarkupRenderer.FIT_NONE);
        limit.setLineTightness(0.5f);
        double atLimit = measure(limit, SMALL_REGION);

        MarkupRenderer r = fitted(MarkupRenderer.FIT_TIGHTEN_LINE_SPACING);
        assertEquals(atLimit, measure(r, SMALL_REGION), 0.01d);
    }

    @Test
    void lowerTightnessLimitPacksLinesCloser() {
        MarkupRenderer r = fitted(MarkupRenderer.FIT_TIGHTEN_LINE_SPACING);
        double defaultLimit = measure(r, SMALL_REGION);
        r.setTightnessLimit(0f);
        assertTrue(measure(r, SMALL_REGION) < defaultLimit);
    }

    @Test
    void tighteningUsesOnlyAsMuchAsNeeded() {
        Rectangle2D wide = new Rectangle2D.Double(0, 0, 300, 400);
        MarkupRenderer limit = fitted(MarkupRenderer.FIT_NONE);
        limit.setLineTightness(0.5f);
        double tightest = measure(limit, wide);
        MarkupRenderer normal = fitted(MarkupRenderer.FIT_NONE);
        double loosest = measure(normal, wide);

        double h = (tightest + loosest) / 2d;
        MarkupRenderer r = fitted(MarkupRenderer.FIT_TIGHTEN_LINE_SPACING);
        Rectangle2D region = new Rectangle2D.Double(0, 0, 300, h);
        double height = measure(r, region);
        assertTrue(height <= h, "height " + height + " limit " + h);
        assertTrue(height > tightest, "height " + height + " tightest " + tightest);
    }

    @Test
    void bottomAlignmentUsesHeightOfTightenedLayout() {
        Rectangle2D wide = new Rectangle2D.Double(0, 0, 300, 400);
        MarkupRenderer limit = fitted(MarkupRenderer.FIT_NONE);
        limit.setLineTightness(0.5f);
        double h = (measure(limit, wide) + measure(fitted(MarkupRenderer.FIT_NONE), wide)) / 2d;
        Rectangle2D region = new Rectangle2D.Double(0, 0, 300, h);

        MarkupRenderer top = fitted(MarkupRenderer.FIT_TIGHTEN_LINE_SPACING);
        MarkupRenderer bottom = fitted(MarkupRenderer.FIT_TIGHTEN_LINE_SPACING);
        bottom.setAlignment(MarkupRenderer.LAYOUT_LEFT | MarkupRenderer.LAYOUT_BOTTOM);
        double offset = firstDrawnY(bottom, region) - firstDrawnY(top, region);
        assertThat(offset).isCloseTo(h - measure(top, region), within(0.01d));
    }

    private static float firstDrawnY(MarkupRenderer r, Rectangle2D region) {
        Graphics2D spy = spyGraphics(400, 500);
        try {
            r.draw(spy, region);
            ArgumentCaptor<Float> y = ArgumentCaptor.forClass(Float.class);
            verify(spy, atLeast(1)).drawGlyphVector(any(GlyphVector.class), anyFloat(), y.capture());
            return y.getAllValues().get(0);
        } finally {
            spy.dispose();
        }
    }

    @Test
    void repeatedMeasureDoesNotDrawAndReturnsSameHeight() {
        MarkupRenderer r = fitted(MarkupRenderer.FIT_BOTH);
        Graphics2D spy = spyGraphics(400, 200);
        try {
            double first = r.measure(spy, SMALL_REGION);
            assertThat(r.measure(spy, SMALL_REGION)).isEqualTo(first);
            verify(spy, never()).drawGlyphVector(any(GlyphVector.class), anyFloat(), anyFloat());
        } finally {
            spy.dispose();
        }
    }

    @Test
    void drawAfterMeasureKeepsVerticalAlignment() {
        Rectangle2D region = new Rectangle2D.Double(0, 0, 300, 200);
        int bottomLeft = MarkupRenderer.LAYOUT_LEFT | MarkupRenderer.LAYOUT_BOTTOM;
        MarkupRenderer drawn = new MarkupRenderer();
        drawn.setAlignment(bottomLeft);
        drawn.setMarkupText("word");
        MarkupRenderer measured = new MarkupRenderer();
        measured.setAlignment(bottomLeft);
        measured.setMarkupText("word");
        measure(measured, region);
        assertThat(firstDrawnY(measured, region)).isEqualTo(firstDrawnY(drawn, region));
    }

    @Test
    void segmentsComeOnlyFromFinalFittedLayout() {
        for (int fit : List.of(MarkupRenderer.FIT_SCALE_TEXT, MarkupRenderer.FIT_TIGHTEN_LINE_SPACING,
                MarkupRenderer.FIT_BOTH)) {
            MarkupRenderer r = fitted(fit);
            List<LayoutSegment> segments = segmentsIn(r, SMALL_REGION);
            assertThat(totalCharacters(segments)).as("fit mode %d", fit).isEqualTo(OVERFLOWING.length());
        }
    }

    @Test
    void repeatedAppendSegmentsDoesNotDrawAndAppendsAgain() {
        MarkupRenderer r = fitted(MarkupRenderer.FIT_BOTH);
        List<LayoutSegment> first = segmentsIn(r, SMALL_REGION);
        Graphics2D spy = spyGraphics(400, 200);
        try {
            List<LayoutSegment> second = new ArrayList<>();
            r.appendSegments(spy, SMALL_REGION, second);
            assertThat(second).hasSameSizeAs(first);
            verify(spy, never()).drawGlyphVector(any(GlyphVector.class), anyFloat(), anyFloat());
        } finally {
            spy.dispose();
        }
    }

    private static List<LayoutSegment> segmentsIn(MarkupRenderer r, Rectangle2D region) {
        List<LayoutSegment> segments = new ArrayList<>();
        try (GraphicsHandle gh = new GraphicsHandle(400, 200)) {
            r.appendSegments(gh.g, region, segments);
        }
        return segments;
    }

    @Test
    void fitBothPrefersTighteningOverScaling() {
        Rectangle2D wide = new Rectangle2D.Double(0, 0, 300, 400);
        double loosest = measure(fitted(MarkupRenderer.FIT_NONE), wide);
        MarkupRenderer limit = fitted(MarkupRenderer.FIT_NONE);
        limit.setLineTightness(0.5f);
        double tightest = measure(limit, wide);
        MarkupRenderer r = fitted(MarkupRenderer.FIT_BOTH);
        measure(r, new Rectangle2D.Double(0, 0, 300, (tightest + loosest) / 2d));
        assertEquals(1d, appliedScale(r));
    }

    @Test
    void fitBothScalesLessThanScalingAlone() {
        MarkupRenderer both = fitted(MarkupRenderer.FIT_BOTH);
        MarkupRenderer scaleOnly = fitted(MarkupRenderer.FIT_SCALE_TEXT);
        measure(both, SMALL_REGION);
        measure(scaleOnly, SMALL_REGION);
        assertTrue(appliedScale(both) > appliedScale(scaleOnly));
    }

    @Test
    void drawAsSingleLineFillsRegionWithFractionalMetrics() {
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText(repeat("Hello, world. ", 10));
        for (double w : List.of(50d, 100d, 200d, 300d)) {
            try (GraphicsHandle gh = new GraphicsHandle(400, 200)) {
                gh.g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                        RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                assertThat(r.drawAsSingleLine(gh.g, new Rectangle2D.Double(0, 0, w, 30)))
                        .as("width in %s-wide region", w)
                        .isCloseTo(w, withinPercentage(2));
            }
        }
    }

    @Test
    void drawAsSingleLineFillsRegionWithoutFractionalMetrics() {
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText(repeat("Hello, world. ", 10));
        for (double w : List.of(50d, 100d, 200d, 300d)) {
            try (GraphicsHandle gh = new GraphicsHandle(400, 200)) {
                assertThat(r.drawAsSingleLine(gh.g, new Rectangle2D.Double(0, 0, w, 30)))
                        .as("width in %s-wide region", w)
                        .isLessThanOrEqualTo(w)
                        .isCloseTo(w, withinPercentage(2));
            }
        }
    }

    @Test
    void drawAsSingleLineDrawsAtFittedWidth() {
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText(repeat("Hello, world. ", 10));
        Graphics2D spy = spyGraphics(400, 200);
        try {
            r.drawAsSingleLine(spy, new Rectangle2D.Double(0, 0, 100, 30));
            ArgumentCaptor<GlyphVector> gv = ArgumentCaptor.forClass(GlyphVector.class);
            verify(spy, atLeast(1)).drawGlyphVector(gv.capture(), anyFloat(), anyFloat());
            assertThat(gv.getValue().getVisualBounds().getWidth()).isLessThanOrEqualTo(100d);
        } finally {
            spy.dispose();
        }
    }

    @Test
    void drawAsSingleLineLeavesFittingTextUnscaled() {
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText("Word");
        try (GraphicsHandle gh = new GraphicsHandle(400, 200)) {
            double width = r.drawAsSingleLine(gh.g, new Rectangle2D.Double(0, 0, 300, 30));
            assertTrue(width < 300d);
        }
        assertEquals(1d, appliedScale(r));
    }

    @Test
    void drawAsSingleLineIgnoresScalingLimit() {
        MarkupRenderer r = new MarkupRenderer();
        r.setScalingLimit(0.5d);
        r.setMarkupText(repeat("Hello, world. ", 10));
        try (GraphicsHandle gh = new GraphicsHandle(400, 200)) {
            r.drawAsSingleLine(gh.g, new Rectangle2D.Double(0, 0, 50, 30));
        }
        assertTrue(appliedScale(r) < 0.5d);
    }

    // ---- Bad-box marking ---------------------------------------------

    @Test
    void markBadBoxRoundTrips() {
        MarkupRenderer r = new MarkupRenderer();
        assertFalse(r.getMarkBadBox());
        r.setMarkBadBox(true);
        assertTrue(r.getMarkBadBox());
    }

    @Test
    void overflowingTextIsMarkedWhenEnabled() {
        MarkupRenderer r = fitted(MarkupRenderer.FIT_NONE);
        r.setMarkBadBox(true);
        Graphics2D spy = spyGraphics(400, 200);
        try {
            Paint original = spy.getPaint();
            r.draw(spy, SMALL_REGION);
            verify(spy).setPaint(Color.RED);
            verify(spy).draw(any(Shape.class));
            assertEquals(original, spy.getPaint());
        } finally {
            spy.dispose();
        }
    }

    @Test
    void overflowingTextIsNotMarkedByDefault() {
        Graphics2D spy = spyGraphics(400, 200);
        try {
            fitted(MarkupRenderer.FIT_NONE).draw(spy, SMALL_REGION);
            verify(spy, never()).draw(any(Shape.class));
        } finally {
            spy.dispose();
        }
    }

    @Test
    void textThatFitsIsNotMarked() {
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkBadBox(true);
        r.setMarkupText("Line");
        Graphics2D spy = spyGraphics(400, 200);
        try {
            r.draw(spy, SMALL_REGION);
            verify(spy, never()).draw(any(Shape.class));
        } finally {
            spy.dispose();
        }
    }

    @Test
    void badBoxOutlinesPageShape() {
        MarkupRenderer r = fitted(MarkupRenderer.FIT_NONE);
        r.setMarkBadBox(true);
        r.setPageShape(new PageShape.InsetShape(5, 5));
        Graphics2D spy = spyGraphics(400, 200);
        try {
            r.draw(spy, SMALL_REGION);
            verify(spy).setPaint(Color.BLUE);
        } finally {
            spy.dispose();
        }
    }

    @Test
    void drawAsSingleLineDoesNotMarkOrChangeSetting() {
        MarkupRenderer r = fitted(MarkupRenderer.FIT_NONE);
        r.setMarkBadBox(true);
        Graphics2D spy = spyGraphics(400, 200);
        try {
            r.drawAsSingleLine(spy, new Rectangle2D.Double(0, 0, 100, 1));
            verify(spy, never()).draw(any(Shape.class));
            assertTrue(r.getMarkBadBox());
        } finally {
            spy.dispose();
        }
    }

    @Test
    void debugFlagOutlinesRegion() {
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText("Line");
        Graphics2D spy = spyGraphics(400, 200);
        MarkupRenderer.DEBUG = true;
        try {
            r.draw(spy, SMALL_REGION);
            r.drawAsSingleLine(spy, SMALL_REGION);
            verify(spy, atLeast(2)).setPaint(Color.MAGENTA);
        } finally {
            MarkupRenderer.DEBUG = false;
            spy.dispose();
        }
    }

    @Test
    void layoutAfterSingleLineMatchesFirstLayout() {
        MarkupRenderer r = fitted(MarkupRenderer.FIT_BOTH);
        double scale;
        try (GraphicsHandle gh = new GraphicsHandle(400, 200)) {
            double height = r.measure(gh.g, SMALL_REGION);
            scale = appliedScale(r);
            r.drawAsSingleLine(gh.g, SMALL_REGION);
            assertThat(r.measure(gh.g, SMALL_REGION)).isCloseTo(height, within(0.01d));
        }
        assertThat(appliedScale(r)).isEqualTo(scale);
    }

    @Test
    void layoutDoesNotHoldCallersRectangle() {
        MarkupRenderer r = fitted(MarkupRenderer.FIT_SCALE_TEXT);
        Rectangle2D.Double region = new Rectangle2D.Double(0, 0, 100, 40);
        measure(r, region);
        double fitted = appliedScale(r);
        region.setRect(0, 0, 1000, 1000);
        measure(r, region);
        assertThat(appliedScale(r)).isGreaterThan(fitted);
    }

    @Test
    void unchangedRectangleReusesLayout() {
        MarkupRenderer r = fitted(MarkupRenderer.FIT_SCALE_TEXT);
        double first = measure(r, SMALL_REGION);
        assertThat(measure(r, new Rectangle2D.Double(0, 0, 100, 40))).isEqualTo(first);
    }

    private static MarkupRenderer fitted(int fitMode) {
        MarkupRenderer r = new MarkupRenderer();
        r.setTextFitting(fitMode);
        r.setMarkupText(OVERFLOWING);
        return r;
    }

    private static double measure(MarkupRenderer r, Rectangle2D region) {
        try (GraphicsHandle gh = new GraphicsHandle(400, 200)) {
            return r.measure(gh.g, region);
        }
    }

    /**
     * Scale factor from the most recent layout, at the 72 dpi used by
     * the no-arg constructor.
     */
    private static double appliedScale(MarkupRenderer r) {
        TransformAttribute scale = (TransformAttribute) r.getStyledText()[0]
                .getIterator().getAttribute(TextAttribute.TRANSFORM);
        return scale.getTransform().getScaleX();
    }

    // ---- Test scaffolding --------------------------------------------

    /**
     * Auto-closing wrapper around a {@link BufferedImage}-backed
     * {@link Graphics2D} so tests can use try-with-resources for
     * deterministic disposal.
     */
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
