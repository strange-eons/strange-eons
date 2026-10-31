package ca.cgjennings.layout;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link PageShape} and its nested shape implementations.
 * Each shape exposes left/right insets as a function of a vertical
 * range; the tests pin those functions and verify the shape interacts
 * correctly with {@code MarkupRenderer}'s layout.
 */
class PageShapeTest {

    // ---- RECTANGLE_SHAPE --------------------------------------------

    @Test
    void rectangleShapeReturnsZeroInsets() {
        // The rectangular default has zero left and right insets at
        // every y range — text fills the region edge-to-edge.
        assertEquals(0.0, PageShape.RECTANGLE_SHAPE.getLeftInset(0, 100));
        assertEquals(0.0, PageShape.RECTANGLE_SHAPE.getRightInset(0, 100));
        assertEquals(0.0, PageShape.RECTANGLE_SHAPE.getLeftInset(50, 60));
        assertEquals(0.0, PageShape.RECTANGLE_SHAPE.getRightInset(50, 60));
    }

    // ---- InsetShape -------------------------------------------------

    @Test
    void insetShapeReturnsConstructedInsetsRegardlessOfY() {
        PageShape s = new PageShape.InsetShape(15, 25);
        assertEquals(15.0, s.getLeftInset(0, 10));
        assertEquals(15.0, s.getLeftInset(100, 200));
        assertEquals(25.0, s.getRightInset(0, 10));
        assertEquals(25.0, s.getRightInset(100, 200));
    }

    @Test
    void insetShapeWithZeroInsetsEquivalentToRectangle() {
        PageShape s = new PageShape.InsetShape(0, 0);
        assertEquals(0.0, s.getLeftInset(10, 20));
        assertEquals(0.0, s.getRightInset(10, 20));
    }

    // ---- CupShape ---------------------------------------------------
    //
    // CupShape(leftTop, rightTop, y, leftBottom, rightBottom):
    // - above the y threshold: top insets
    // - below the y threshold: bottom insets
    // - spanning the threshold: max of top and bottom

    @Test
    void cupShapeReturnsTopInsetsAboveThreshold() {
        // y=50 threshold; line range (10, 40) is entirely above.
        PageShape.CupShape cup = new PageShape.CupShape(10, 20, 50, 5, 15);
        assertEquals(10.0, cup.getLeftInset(10, 40));
        assertEquals(20.0, cup.getRightInset(10, 40));
    }

    @Test
    void cupShapeReturnsBottomInsetsBelowThreshold() {
        // Line range (60, 80) is entirely below the y=50 threshold.
        PageShape.CupShape cup = new PageShape.CupShape(10, 20, 50, 5, 15);
        assertEquals(5.0, cup.getLeftInset(60, 80));
        assertEquals(15.0, cup.getRightInset(60, 80));
    }

    @Test
    void cupShapeReturnsMaxWhenSpanningThreshold() {
        // Line range (40, 60) spans y=50. Should return max of top
        // and bottom insets on each side.
        PageShape.CupShape cup = new PageShape.CupShape(10, 20, 50, 5, 25);
        assertEquals(10.0, cup.getLeftInset(40, 60),
                "expected max(10, 5) for spanning left inset");
        assertEquals(25.0, cup.getRightInset(40, 60),
                "expected max(20, 25) for spanning right inset");
    }

    @Test
    void cupShapeEqualityRespectsAllFields() {
        PageShape.CupShape a = new PageShape.CupShape(10, 20, 50, 5, 15);
        PageShape.CupShape b = new PageShape.CupShape(10, 20, 50, 5, 15);
        PageShape.CupShape different = new PageShape.CupShape(11, 20, 50, 5, 15);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, different);
    }

    // ---- CompoundShape ----------------------------------------------
    //
    // CompoundShape(top, y, bottom): switches between the two shapes
    // at the y boundary; behavior at the boundary takes the max.

    @Test
    void compoundShapeUsesTopShapeAboveBoundary() {
        PageShape top = new PageShape.InsetShape(10, 20);
        PageShape bottom = new PageShape.InsetShape(30, 40);
        PageShape compound = new PageShape.CompoundShape(top, 50, bottom);
        assertEquals(10.0, compound.getLeftInset(10, 30));
        assertEquals(20.0, compound.getRightInset(10, 30));
    }

    @Test
    void compoundShapeUsesBottomShapeBelowBoundary() {
        PageShape top = new PageShape.InsetShape(10, 20);
        PageShape bottom = new PageShape.InsetShape(30, 40);
        PageShape compound = new PageShape.CompoundShape(top, 50, bottom);
        assertEquals(30.0, compound.getLeftInset(60, 80));
        assertEquals(40.0, compound.getRightInset(60, 80));
    }

    @Test
    void compoundShapeReturnsMaxAcrossBoundary() {
        // Line spans the boundary — pick the larger of the two
        // shapes' insets on each side.
        PageShape top = new PageShape.InsetShape(10, 40);
        PageShape bottom = new PageShape.InsetShape(30, 20);
        PageShape compound = new PageShape.CompoundShape(top, 50, bottom);
        assertEquals(30.0, compound.getLeftInset(40, 60),
                "expected max(10, 30) = 30 for boundary-spanning left inset");
        assertEquals(40.0, compound.getRightInset(40, 60),
                "expected max(40, 20) = 40 for boundary-spanning right inset");
    }

    // ---- MergedShape ------------------------------------------------

    @Test
    void mergedShapeReturnsMaxOfTwoShapeInsets() {
        // MergedShape takes the union (max) of two shapes' insets at
        // every y range.
        PageShape a = new PageShape.InsetShape(10, 30);
        PageShape b = new PageShape.InsetShape(20, 15);
        PageShape merged = new PageShape.MergedShape(a, b);
        assertEquals(20.0, merged.getLeftInset(0, 100),
                "expected max(10, 20) = 20");
        assertEquals(30.0, merged.getRightInset(0, 100),
                "expected max(30, 15) = 30");
    }

    @Test
    void mergedShapeWithRectangleNarrowsToOther() {
        // Merging RECTANGLE_SHAPE (zero insets) with another shape
        // yields the other shape's insets.
        PageShape other = new PageShape.InsetShape(15, 25);
        PageShape merged = new PageShape.MergedShape(
                PageShape.RECTANGLE_SHAPE, other);
        assertEquals(15.0, merged.getLeftInset(0, 100));
        assertEquals(25.0, merged.getRightInset(0, 100));
    }

    // ---- Integration with MarkupRenderer ----------------------------

    @Test
    void renderingWithInsetShapeNarrowsAvailableWidth() {
        // With an InsetShape applied, wrapped lines should fit
        // within the narrowed effective width — the layout should
        // produce more line breaks than the same content rendered
        // without insets.
        String text = repeat("word ", 30);

        int linesRectangle = countLines(text, PageShape.RECTANGLE_SHAPE, 200);
        int linesNarrowed = countLines(text, new PageShape.InsetShape(40, 40), 200);

        assertTrue(linesNarrowed > linesRectangle,
                "expected narrowing insets to increase line count; "
                        + "rectangle=" + linesRectangle
                        + " narrowed=" + linesNarrowed);
    }

    @Test
    void renderingWithCupShapeProducesValidLayout() {
        // Smoke test: rendering through a CupShape doesn't throw and
        // still produces line-start segments.
        MarkupRenderer r = new MarkupRenderer();
        r.setPageShape(new PageShape.CupShape(20, 20, 30, 0, 0));
        r.setMarkupText(repeat("word ", 20));

        java.util.List<LayoutSegment> segments = new java.util.ArrayList<>();
        try (GraphicsHandle gh = new GraphicsHandle(400, 200)) {
            r.appendSegments(gh.g, new Rectangle2D.Double(0, 0, 200, 100), segments);
        }
        long lineStarts = segments.stream()
                .filter(LayoutSegment::isStartOfLine)
                .count();
        assertTrue(lineStarts >= 1,
                "expected at least one line-start segment with CupShape; got "
                        + lineStarts);
    }

    // ---- Helpers ----------------------------------------------------

    private static int countLines(String markup, PageShape shape, double regionWidth) {
        MarkupRenderer r = new MarkupRenderer();
        r.setPageShape(shape);
        r.setMarkupText(markup);
        java.util.List<LayoutSegment> segments = new java.util.ArrayList<>();
        try (GraphicsHandle gh = new GraphicsHandle((int) regionWidth + 200, 600)) {
            r.appendSegments(gh.g, new Rectangle2D.Double(0, 0, regionWidth, 500), segments);
        }
        return (int) segments.stream()
                .filter(LayoutSegment::isStartOfLine)
                .count();
    }

    private static String repeat(String s, int times) {
        StringBuilder sb = new StringBuilder(s.length() * times);
        for (int i = 0; i < times; i++) {
            sb.append(s);
        }
        return sb.toString();
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

    // ---- Sanity: RECTANGLE_SHAPE is a stable singleton --------------

    @Test
    void rectangleShapeIsStableSingleton() {
        assertSame(PageShape.RECTANGLE_SHAPE, PageShape.RECTANGLE_SHAPE);
    }
}
