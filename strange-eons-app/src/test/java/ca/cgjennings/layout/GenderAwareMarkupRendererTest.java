package ca.cgjennings.layout;

import java.awt.Graphics2D;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Coverage for {@link GenderAwareMarkupRenderer}'s tag-replacement
 * behavior. The subclass extends {@code MarkupRenderer} only via
 * {@code handleUnknownTag}, so the parent's contract is already
 * covered by {@code MarkupRendererTest}; tests here focus on
 * gender-tag dispatch (left/right segment selection, multi-segment,
 * hyphen-segment-as-empty).
 */
class GenderAwareMarkupRendererTest {

    // ---- Gender state ------------------------------------------------

    @Test
    void defaultGenderIsZero() {
        GenderAwareMarkupRenderer r = new GenderAwareMarkupRenderer();
        assertEquals(0, r.getGender());
    }

    @Test
    void setGenderBooleanFalseSelectsLeftSegment() {
        GenderAwareMarkupRenderer r = new GenderAwareMarkupRenderer();
        r.setGender(true);
        r.setGender(false);
        assertEquals(0, r.getGender());
    }

    @Test
    void setGenderBooleanTrueSelectsRightSegment() {
        GenderAwareMarkupRenderer r = new GenderAwareMarkupRenderer();
        r.setGender(true);
        assertEquals(1, r.getGender());
    }

    @Test
    void setGenderIntRoundTrips() {
        GenderAwareMarkupRenderer r = new GenderAwareMarkupRenderer();
        r.setGender(2);
        assertEquals(2, r.getGender());
        r.setGender(3);
        assertEquals(3, r.getGender());
    }

    @Test
    void genderConstantsExposeIndices() {
        assertEquals(0, GenderAwareMarkupRenderer.GENDER_LEFT);
        assertEquals(1, GenderAwareMarkupRenderer.GENDER_MIDDLE);
        assertEquals(2, GenderAwareMarkupRenderer.GENDER_RIGHT);
    }

    // ---- Gender-tag dispatch -----------------------------------------

    @Test
    void leftSegmentSelectedWhenGenderZero() {
        // <Jack/Diane> with gender=0 should render "Jack" (4 chars).
        // Compare against the rendered glyph count for a known string.
        GenderAwareMarkupRenderer r = new GenderAwareMarkupRenderer();
        r.setGender(0);
        int genderGlyphs = renderAndCountGlyphs(r, "<Jack/Diane>");
        int plainGlyphs = renderAndCountGlyphs(new MarkupRenderer(), "Jack");
        assertEquals(plainGlyphs, genderGlyphs,
                "expected <Jack/Diane> with gender=0 to render 'Jack'; "
                        + "gender=" + genderGlyphs + " plain=" + plainGlyphs);
    }

    @Test
    void rightSegmentSelectedWhenGenderOne() {
        // <Jack/Diane> with gender=1 should render "Diane" (5 chars).
        GenderAwareMarkupRenderer r = new GenderAwareMarkupRenderer();
        r.setGender(1);
        int genderGlyphs = renderAndCountGlyphs(r, "<Jack/Diane>");
        int plainGlyphs = renderAndCountGlyphs(new MarkupRenderer(), "Diane");
        assertEquals(plainGlyphs, genderGlyphs,
                "expected <Jack/Diane> with gender=1 to render 'Diane'; "
                        + "gender=" + genderGlyphs + " plain=" + plainGlyphs);
    }

    @Test
    void multiSegmentTagSelectsThirdWhenGenderTwo() {
        // <a/b/c/d> with gender=2 should render "c".
        GenderAwareMarkupRenderer r = new GenderAwareMarkupRenderer();
        r.setGender(2);
        int genderGlyphs = renderAndCountGlyphs(r, "<a/b/c/d>");
        int plainGlyphs = renderAndCountGlyphs(new MarkupRenderer(), "c");
        assertEquals(plainGlyphs, genderGlyphs,
                "expected <a/b/c/d> with gender=2 to render 'c'; "
                        + "gender=" + genderGlyphs + " plain=" + plainGlyphs);
    }

    @Test
    void hyphenSegmentProducesEmptyString() {
        // <-/foo> with gender=0 should render nothing (hyphen → "").
        // priest<-/ess> with gender=0 should render "priest" only.
        GenderAwareMarkupRenderer r = new GenderAwareMarkupRenderer();
        r.setGender(0);
        int withTag = renderAndCountGlyphs(r, "priest<-/ess>");
        int plain = renderAndCountGlyphs(new MarkupRenderer(), "priest");
        assertEquals(plain, withTag,
                "expected priest<-/ess> with gender=0 to render 'priest' only; "
                        + "withTag=" + withTag + " plain=" + plain);
    }

    @Test
    void hyphenSegmentInOtherPositionExpandsToText() {
        // priest<-/ess> with gender=1 should render "priestess".
        GenderAwareMarkupRenderer r = new GenderAwareMarkupRenderer();
        r.setGender(1);
        int withTag = renderAndCountGlyphs(r, "priest<-/ess>");
        int plain = renderAndCountGlyphs(new MarkupRenderer(), "priestess");
        assertEquals(plain, withTag,
                "expected priest<-/ess> with gender=1 to render 'priestess'; "
                        + "withTag=" + withTag + " plain=" + plain);
    }

    @Test
    void parentClassUnknownTagHandlingStillWorks() {
        // <u+221E> infinity escape (handled by super.handleUnknownTag)
        // should still resolve in the subclass.
        GenderAwareMarkupRenderer r = new GenderAwareMarkupRenderer();
        r.setMarkupText("a <u+221E> b");
        Graphics2D g = newImage(400, 200).createGraphics();
        try {
            r.draw(g, new Rectangle2D.Double(0, 0, 300, 100));
        } finally {
            g.dispose();
        }
    }

    @Test
    void tagWithoutSlashIsTreatedAsUnknown() {
        // <Jack> without a slash is not a gender tag; it should fall
        // through to be dropped (or treated as unknown by the parent).
        // Verify it doesn't add glyphs beyond the surrounding text.
        GenderAwareMarkupRenderer r = new GenderAwareMarkupRenderer();
        int withTag = renderAndCountGlyphs(r, "before <Jack> after");
        int plain = renderAndCountGlyphs(new MarkupRenderer(), "before  after");
        assertEquals(plain, withTag,
                "expected unknown tag without slash to be dropped; "
                        + "withTag=" + withTag + " plain=" + plain);
    }

    @Test
    void slashAtStartIsRejected() {
        // </foo> looks like a close tag, not a gender tag. The
        // handler skips when slash is at position < 1.
        GenderAwareMarkupRenderer r = new GenderAwareMarkupRenderer();
        r.setMarkupText("a </tag> b");
        // Should not throw.
        Graphics2D g = newImage(400, 200).createGraphics();
        try {
            r.draw(g, new Rectangle2D.Double(0, 0, 300, 100));
        } finally {
            g.dispose();
        }
    }

    // ---- Capitalize helper -------------------------------------------

    @Test
    void capitalizeSingleCharUsesNoLocale() {
        // Single-char capitalization uses Java's default-locale
        // toUpperCase, which works without Strange Eons' game locale
        // being set.
        assertEquals("A", GenderAwareMarkupRenderer.capitalize("a"));
    }

    @Test
    void capitalizeEmptyStringReturnsEmpty() {
        assertEquals("", GenderAwareMarkupRenderer.capitalize(""));
    }

    // ---- Helpers -----------------------------------------------------

    /**
     * Sets the markup on {@code r}, renders into a spy-wrapped
     * Graphics2D, and returns the total number of glyphs drawn.
     */
    private static int renderAndCountGlyphs(MarkupRenderer r, String markup) {
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

    private static BufferedImage newImage(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }
}
