package ca.cgjennings.layout;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.font.GraphicAttribute;
import java.awt.font.TextAttribute;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.AttributedCharacterIterator;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import resources.StrangeImage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Covers the {@code <image>} tag, {@link GraphicStyleFactory} and
 * {@link StrangeImageAttribute}. Sizes use 72 ppi, so one inch is 72 pixels. The
 * fixture is 100 by 50 pixels, which defaults to 48 by 24 at that resolution.
 */
class MarkupRendererImageTagTest {

    private static final double PPI = 72d;
    private static final int RED = 0xffff0000;

    @TempDir
    File dir;

    private File picture;
    private String url;
    private final GraphicStyleFactory factory = new GraphicStyleFactory(PPI);
    private final MarkupRenderer renderer = new MarkupRenderer(PPI);

    @BeforeEach
    void writePicture() throws IOException {
        BufferedImage bi = new BufferedImage(100, 50, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 100, 50);
        g.dispose();
        picture = new File(dir, "picture.png");
        ImageIO.write(bi, "png", picture);
        url = picture.toURI().toString();
    }

    @Test
    void defaultSizeAssumes150PpiAndKeepsAspectRatio() {
        assertSize(attribute(), 48f, 24f);
    }

    @Test
    void widthSetsHeightFromAspectRatio() {
        assertSize(attribute("1in"), 72f, 36f);
    }

    @Test
    void widthAndHeightAreIndependent() {
        assertSize(attribute("1in", "2in"), 72f, 144f);
    }

    @Test
    void measurementsAcceptUnits() {
        assertSize(attribute("36pt", "2.54cm"), 36f, 72f);
    }

    @Test
    void sizeFollowsResolution() {
        factory.setResolution(144d);
        assertThat(factory.getResolution()).isEqualTo(144d);
        assertSize(attribute("1in", "1in"), 144f, 144f);
    }

    @Test
    void nonPositiveSizesAreSkipped() {
        assertSize(attribute("0", "-1in", "1in"), 72f, 36f);
        assertSize(attribute("1in", "0", "1in"), 72f, 72f);
    }

    @Test
    void unparseableSizesAreSkipped() {
        assertSize(attribute("wide", "1in"), 72f, 36f);
    }

    @Test
    void extraMeasurementsAreIgnored() {
        StrangeImageAttribute a = attribute("1in", "1in", "0.5in", "2in");
        assertSize(a, 72f, 72f);
        assertThat(a.getAscent()).isEqualTo(36f);
    }

    @Test
    void defaultAlignmentPlacesBottomOnBaseline() {
        StrangeImageAttribute a = attribute("1in", "1in");
        assertThat(a.getAlignment()).isEqualTo(GraphicAttribute.ROMAN_BASELINE);
        assertThat(a.getAscent()).isEqualTo(72f);
        assertThat(a.getDescent()).isZero();
    }

    @Test
    void baselineAlignmentMatchesDefault() {
        assertThat(attribute("1in", "1in", "baseline")).isEqualTo(attribute("1in", "1in"));
    }

    @Test
    void centerAlignmentNamesAreEquivalentAndDefaultToMiddle() {
        for (String name : new String[]{"center", "centre", "middle", "CENTER"}) {
            StrangeImageAttribute a = attribute("1in", "1in", name);
            assertThat(a.getAlignment()).as(name).isEqualTo(GraphicAttribute.CENTER_BASELINE);
            assertThat(a.getAscent()).as(name).isEqualTo(36f);
            assertThat(a.getDescent()).as(name).isEqualTo(36f);
        }
    }

    @Test
    void hangingAlignmentDefaultsToTopOfImage() {
        StrangeImageAttribute a = attribute("1in", "1in", "hanging");
        assertThat(a.getAlignment()).isEqualTo(GraphicAttribute.HANGING_BASELINE);
        assertThat(a.getAscent()).isZero();
        assertThat(a.getDescent()).isEqualTo(72f);
    }

    @Test
    void topAlignmentIgnoresOffset() {
        StrangeImageAttribute a = attribute("1in", "1in", "top", "0.5in");
        assertThat(a.getAlignment()).isEqualTo(GraphicAttribute.TOP_ALIGNMENT);
        assertThat(a.getAscent()).isZero();
    }

    @Test
    void bottomAlignmentIgnoresOffset() {
        StrangeImageAttribute a = attribute("1in", "1in", "bottom", "0.5in");
        assertThat(a.getAlignment()).isEqualTo(GraphicAttribute.BOTTOM_ALIGNMENT);
        assertThat(a.getAscent()).isEqualTo(72f);
    }

    @Test
    void offsetAfterAlignmentMeasuresFromTop() {
        StrangeImageAttribute a = attribute("1in", "1in", "center", "0.25in");
        assertThat(a.getAscent()).isEqualTo(18f);
        assertThat(a.getDescent()).isEqualTo(54f);
    }

    @Test
    void offsetAfterAlignmentNeedsNoSize() {
        StrangeImageAttribute a = attribute("center", "0.25in");
        assertSize(a, 48f, 24f);
        assertThat(a.getAscent()).isEqualTo(18f);
    }

    @Test
    void measurementsAfterOffsetAreIgnored() {
        StrangeImageAttribute a = attribute("center", "0.25in", "1in");
        assertSize(a, 48f, 24f);
        assertThat(a.getAscent()).isEqualTo(18f);
    }

    @Test
    void offsetBeyondImageHasNoDescent() {
        StrangeImageAttribute a = attribute("1in", "1in", "center", "2in");
        assertThat(a.getAscent()).isEqualTo(144f);
        assertThat(a.getDescent()).isZero();
    }

    @Test
    void negativeOffsetAfterSizeHasNoAscent() {
        StrangeImageAttribute a = attribute("1in", "1in", "center", "-0.5in");
        assertThat(a.getAscent()).isZero();
        assertThat(a.getDescent()).isEqualTo(108f);
        assertThat(a.getBounds()).isEqualTo(new Rectangle2D.Float(0f, 36f, 72f, 72f));
    }

    @Test
    void lastAlignmentWins() {
        assertThat(attribute("1in", "top", "hanging").getAlignment())
                .isEqualTo(GraphicAttribute.HANGING_BASELINE);
    }

    @Test
    @Disabled("BUG: offset of zero is dropped unless width and height are given")
    void offsetOfZeroAlignsTopOfImage() {
        assertThat(attribute("center", "0").getAscent()).isZero();
    }

    @Test
    @Disabled("BUG: unparseable offset is read as -1 inch")
    void unparseableOffsetUsesDefault() {
        assertThat(attribute("1in", "1in", "center", "bogus").getAscent()).isEqualTo(36f);
    }

    @Test
    @Disabled("BUG: unparseable measurement is read as -1 inch")
    void unparseableMeasurementAfterSizeIsSkipped() {
        assertThat(attribute("1in", "1in", "bogus").getAscent()).isEqualTo(72f);
    }

    @Test
    void missingFileUsesMissingImage() {
        StrangeImageAttribute a = attributeFor(new File(dir, "none.png").toURI().toString(), "1in", "1in");
        StrangeImageAttribute missing = (StrangeImageAttribute) factory
                .createStyle(StrangeImage.getMissingImage(), GraphicAttribute.ROMAN_BASELINE, 72d, 72d, 0f, 72f)
                .get(TextAttribute.CHAR_REPLACEMENT);
        assertThat(a).isEqualTo(missing);
    }

    @Test
    void noParametersUsesMissingImage() {
        TextStyle style = factory.createStyle(renderer, new String[0]);
        assertThat(style.get(TextAttribute.CHAR_REPLACEMENT))
                .isEqualTo(factory.createStyle(StrangeImage.getMissingImage()).get(TextAttribute.CHAR_REPLACEMENT));
    }

    @Test
    void imageOnlyStyleUses72PpiAndBottomAlignment() {
        StrangeImage image = StrangeImage.get(url);
        StrangeImageAttribute a = (StrangeImageAttribute) factory.createStyle(image).get(TextAttribute.CHAR_REPLACEMENT);
        assertThat(a.getAlignment()).isEqualTo(GraphicAttribute.BOTTOM_ALIGNMENT);
        assertThat(a.getAdvance()).isCloseTo(100f / 72f, within(1e-5f));
        assertThat(a.getDescent()).isCloseTo(50f / 72f, within(1e-5f));
    }

    @Test
    void explicitOriginAdjustsAdvanceAndBounds() {
        StrangeImage image = StrangeImage.get(url);
        StrangeImageAttribute a = (StrangeImageAttribute) factory
                .createStyle(image, GraphicAttribute.ROMAN_BASELINE, 20d, 10d, 5f, 4f)
                .get(TextAttribute.CHAR_REPLACEMENT);
        assertThat(a.getAdvance()).isEqualTo(15f);
        assertThat(a.getAscent()).isEqualTo(4f);
        assertThat(a.getDescent()).isEqualTo(6f);
        assertThat(a.getBounds()).isEqualTo(new Rectangle2D.Float(-5f, -4f, 20f, 10f));
    }

    @Test
    void originPastEdgesClampsMetrics() {
        StrangeImage image = StrangeImage.get(url);
        StrangeImageAttribute a = (StrangeImageAttribute) factory
                .createStyle(image, GraphicAttribute.ROMAN_BASELINE, 20d, 10d, 25f, -1f)
                .get(TextAttribute.CHAR_REPLACEMENT);
        assertThat(a.getAdvance()).isZero();
        assertThat(a.getAscent()).isZero();
    }

    @Test
    void attributeEqualityUsesEveryField() {
        StrangeImage image = StrangeImage.get(url);
        StrangeImageAttribute a = new StrangeImageAttribute(image, 0, 1f, 2f, 3f, 4f);
        assertThat(a).isEqualTo(a);
        assertThat(a).isEqualTo(new StrangeImageAttribute(image, 0, 1f, 2f, 3f, 4f));
        assertThat(a).hasSameHashCodeAs(new StrangeImageAttribute(image, 0, 1f, 2f, 3f, 4f));
        assertThat(a.equals((StrangeImageAttribute) null)).isFalse();
        assertThat(a.equals((Object) "other")).isFalse();
        assertThat(a).isNotEqualTo(new StrangeImageAttribute(image, 1, 1f, 2f, 3f, 4f));
        assertThat(a).isNotEqualTo(new StrangeImageAttribute(image, 0, 9f, 2f, 3f, 4f));
        assertThat(a).isNotEqualTo(new StrangeImageAttribute(image, 0, 1f, 9f, 3f, 4f));
        assertThat(a).isNotEqualTo(new StrangeImageAttribute(image, 0, 1f, 2f, 9f, 4f));
        assertThat(a).isNotEqualTo(new StrangeImageAttribute(image, 0, 1f, 2f, 3f, 9f));
        assertThat(a).isNotEqualTo(new StrangeImageAttribute(StrangeImage.getMissingImage(), 0, 1f, 2f, 3f, 4f));
    }

    @Test
    void tagInsertsObjectReplacementCharacterWithAttribute() {
        renderer.setMarkupText("a<image \"" + url + "\" 12pt>b");
        measure();
        AttributedCharacterIterator it = renderer.getStyledText()[0].getIterator();
        assertThat(it.first()).isEqualTo('a');
        assertThat(it.getAttribute(TextAttribute.CHAR_REPLACEMENT)).isNull();
        assertThat(it.next()).isEqualTo('￼');
        assertThat(it.getAttribute(TextAttribute.CHAR_REPLACEMENT)).isInstanceOf(StrangeImageAttribute.class);
        assertThat(it.next()).isEqualTo('b');
        assertThat(it.getAttribute(TextAttribute.CHAR_REPLACEMENT)).isNull();
    }

    @Test
    void tagNameIsCaseInsensitive() {
        renderer.setMarkupText("<IMAGE \"" + url + "\" 12pt>");
        measure();
        assertThat(renderer.getStyledText()[0].getIterator().first()).isEqualTo('￼');
    }

    @Test
    void tagWithoutParametersIsNotAnImage() {
        renderer.setMarkupText("a<image>b");
        measure();
        AttributedCharacterIterator it = renderer.getStyledText()[0].getIterator();
        assertThat(it.first()).isEqualTo('a');
        assertThat(it.next()).isEqualTo('b');
    }

    @Test
    void renderedImageHasRequestedSize() {
        renderer.setMarkupText("<image \"" + url + "\" 1in 0.5in>");
        Rectangle box = redBounds();
        assertThat(box.x).isZero();
        assertThat(box.width).isEqualTo(72);
        assertThat(box.height).isEqualTo(36);
    }

    @Test
    void imageTallerThanTextGrowsLine() {
        renderer.setMarkupText("<image \"" + url + "\" 1in 1.5in>");
        double height = measure();
        assertThat(height).isGreaterThanOrEqualTo(108d);
    }

    @Test
    @Disabled("BUG: relative paths are never resolved against the base file")
    void relativePathUsesBaseFile() {
        renderer.setBaseFile(dir);
        assertSize(attributeFor(picture.getName()), 48f, 24f);
    }

    @Test
    @Disabled("BUG: colon check is inverted")
    void translateRelativePathResolvesAgainstBase() {
        assertThat(GraphicStyleFactory.translateRelativePath("a.png", dir))
                .isEqualTo(new File(dir, "a.png").getAbsolutePath());
    }

    @Test
    @Disabled("BUG: colon check is inverted")
    void translateRelativePathKeepsUrl() {
        assertThat(GraphicStyleFactory.translateRelativePath("https://example.com/a.png", dir))
                .isEqualTo("https://example.com/a.png");
    }

    @Test
    void translateRelativePathWithoutBaseKeepsPath() {
        assertThat(GraphicStyleFactory.translateRelativePath("a.png", null)).isEqualTo("a.png");
    }

    @Test
    void translateRelativePathKeepsAbsolutePath() {
        String absolute = picture.getAbsolutePath();
        assertThat(GraphicStyleFactory.translateRelativePath(absolute, dir)).isEqualTo(absolute);
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedHelpersDelegateToStrangeImage() {
        assertThat(GraphicStyleFactory.imageExists(url)).isTrue();
        assertThat(GraphicStyleFactory.imageExists(new File(dir, "none.png").toURI().toString())).isFalse();
        assertThat(GraphicStyleFactory.isURLString(url)).isTrue();
        assertThat(GraphicStyleFactory.isURLString("a.png")).isFalse();
        assertThat(GraphicStyleFactory.translatePathToImageURL(url).toString()).isEqualTo(StrangeImage.identifierToURL(url).toString());
        assertThat(GraphicStyleFactory.fetchImage(url).getWidth()).isEqualTo(100);
        assertThat(GraphicStyleFactory.getMissingImage()).isNotNull();
    }

    private StrangeImageAttribute attribute(String... parameters) {
        return attributeFor(url, parameters);
    }

    private StrangeImageAttribute attributeFor(String name, String... parameters) {
        String[] all = new String[parameters.length + 1];
        all[0] = name;
        System.arraycopy(parameters, 0, all, 1, parameters.length);
        return (StrangeImageAttribute) factory.createStyle(renderer, all).get(TextAttribute.CHAR_REPLACEMENT);
    }

    private static void assertSize(StrangeImageAttribute a, float width, float height) {
        Rectangle2D bounds = a.getBounds();
        assertThat(bounds.getWidth()).isCloseTo(width, within(1e-3));
        assertThat(bounds.getHeight()).isCloseTo(height, within(1e-3));
        assertThat(a.getAdvance()).isCloseTo(width, within(1e-3f));
    }

    private double measure() {
        Graphics2D g = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
            return renderer.measure(g, new Rectangle2D.Double(0, 0, 400, 300));
        } finally {
            g.dispose();
        }
    }

    private Rectangle redBounds() {
        BufferedImage target = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = target.createGraphics();
        try {
            renderer.draw(g, new Rectangle2D.Double(0, 0, 400, 300));
        } finally {
            g.dispose();
        }
        Rectangle box = null;
        for (int y = 0; y < target.getHeight(); ++y) {
            for (int x = 0; x < target.getWidth(); ++x) {
                if (target.getRGB(x, y) == RED) {
                    if (box == null) {
                        box = new Rectangle(x, y, 1, 1);
                    } else {
                        box.add(new Rectangle(x, y, 1, 1));
                    }
                }
            }
        }
        assertThat(box).as("image pixels").isNotNull();
        return box;
    }
}
