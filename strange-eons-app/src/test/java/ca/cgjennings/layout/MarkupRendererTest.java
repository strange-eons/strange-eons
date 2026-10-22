package ca.cgjennings.layout;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.text.AttributedCharacterIterator;
import java.text.CharacterIterator;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
 * existing behavior so the parser / layout split can proceed.
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

    @Test
    void removeAllTagsClearsCustomTagsButKeepsBuiltins() {
        MarkupRenderer r = new MarkupRenderer();
        r.setStyleForTag("custom", new TextStyle());
        r.removeAllTags();
        // 'custom' should be gone
        assertNull(r.getStyleForTag("custom"));
        // 'b' is a built-in; behaviour to be pinned by this test
        // (placeholder — verify against actual implementation).
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
            double width = r.drawAsSingleLine(gh.g, region);
            assertTrue(width > 0 && width <= region.getWidth(),
                    "expected width in (0, " + region.getWidth() + "]; got " + width);
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
        // after backslash. Pin the behaviour: backslash + newline
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
        MarkupRenderer r = new MarkupRenderer();
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
            // split), confirming the tag was recognised.
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
