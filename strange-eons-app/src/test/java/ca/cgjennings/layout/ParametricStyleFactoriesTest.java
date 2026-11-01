package ca.cgjennings.layout;

import java.awt.Color;
import java.awt.font.TextAttribute;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the individual {@link ParametricStyleFactory}
 * implementations used by {@code MarkupRenderer}. Each factory has a
 * small surface — parse the parameter string(s), produce a
 * {@link TextStyle} carrying the corresponding {@link TextAttribute}.
 * These tests verify the parse contracts in isolation from the
 * renderer.
 */
class ParametricStyleFactoriesTest {

    // ---- ForegroundColorStyleFactory.parseColor ---------------------

    @Test
    void parseColorAcceptsSixHexCharsWithoutHash() {
        Color c = ForegroundColorStyleFactory.parseColor(new String[] {"FF0000"});
        assertEquals(new Color(0xFF0000), c);
    }

    @Test
    void parseColorAcceptsSixHexCharsWithHash() {
        Color c = ForegroundColorStyleFactory.parseColor(new String[] {"#FF0000"});
        assertEquals(new Color(0xFF0000), c);
    }

    @Test
    void parseColorAcceptsEightHexCharsForAlpha() {
        // AARRGGBB form: 80FF0000 = half-alpha red
        Color c = ForegroundColorStyleFactory.parseColor(new String[] {"80FF0000"});
        assertNotNull(c);
        assertEquals(0x80, c.getAlpha());
        assertEquals(255, c.getRed());
        assertEquals(0, c.getGreen());
        assertEquals(0, c.getBlue());
    }

    @Test
    void parseColorAcceptsHsbTriple() {
        // Hue 0, saturation 1, brightness 1 = pure red
        Color c = ForegroundColorStyleFactory.parseColor(
                new String[] {"0", "1", "1"});
        assertNotNull(c);
        assertEquals(255, c.getRed());
        assertEquals(0, c.getGreen());
        assertEquals(0, c.getBlue());
    }

    @Test
    void parseColorAcceptsHsbaQuadruple() {
        // HSB red + alpha 0.5 ≈ 128
        Color c = ForegroundColorStyleFactory.parseColor(
                new String[] {"0", "1", "1", "0.5"});
        assertNotNull(c);
        assertEquals(255, c.getRed());
        // Alpha rounds to 128 via Math.round(0.5 * 255).
        assertEquals(128, c.getAlpha());
    }

    @Test
    void parseColorClipsHsbSaturationToOne() {
        // Saturation > 1 should clip to 1 (clipping is the second arg).
        Color c = ForegroundColorStyleFactory.parseColor(
                new String[] {"120", "5", "1"});
        Color clipped = ForegroundColorStyleFactory.parseColor(
                new String[] {"120", "1", "1"});
        assertEquals(clipped.getRGB(), c.getRGB(),
                "expected saturation > 1 to clip to 1");
    }

    @Test
    void parseColorReturnsNullForUnparseableInput() {
        // Non-hex single param.
        assertNull(ForegroundColorStyleFactory.parseColor(new String[] {"garbage"}));
    }

    @Test
    void parseColorReturnsNullForUnsupportedParameterCount() {
        // Zero params (factory expects 1, 3, or 4) — null.
        assertNull(ForegroundColorStyleFactory.parseColor(new String[] {}));
        // Two params — null.
        assertNull(ForegroundColorStyleFactory.parseColor(new String[] {"a", "b"}));
    }

    @Test
    void foregroundFactoryStyleCarriesForegroundAttribute() {
        TextStyle style = ForegroundColorStyleFactory.getShared()
                .createStyle(null, new String[] {"00FF00"});
        assertTrue(style.contains(TextAttribute.FOREGROUND));
        assertEquals(new Color(0x00FF00), style.get(TextAttribute.FOREGROUND));
    }

    @Test
    void foregroundFactorySharedInstanceIsStable() {
        assertSame(ForegroundColorStyleFactory.getShared(),
                ForegroundColorStyleFactory.getShared());
    }

    // ---- BackgroundColorStyleFactory --------------------------------

    @Test
    void backgroundFactoryStyleCarriesBackgroundAttribute() {
        TextStyle style = BackgroundColorStyleFactory.getShared()
                .createStyle(null, new String[] {"0000FF"});
        assertTrue(style.contains(TextAttribute.BACKGROUND));
        assertEquals(new Color(0x0000FF), style.get(TextAttribute.BACKGROUND));
        // Background factory must not also set foreground.
        assertFalse(style.contains(TextAttribute.FOREGROUND));
    }

    @Test
    void backgroundFactoryReusesForegroundParseColor() {
        // Both factories share the parseColor method (Background
        // extends Foreground). HSB input works on either.
        TextStyle style = BackgroundColorStyleFactory.getShared()
                .createStyle(null, new String[] {"240", "1", "1"});
        assertEquals(new Color(0x0000FF), style.get(TextAttribute.BACKGROUND));
    }

    // ---- FontSizeStyleFactory ---------------------------------------

    @Test
    void fontSizeFactoryParsesNumericValue() {
        TextStyle style = FontSizeStyleFactory.getShared()
                .createStyle(null, new String[] {"24"});
        assertEquals(24f, ((Number) style.get(TextAttribute.SIZE)).floatValue(), 0.001f);
    }

    @Test
    void fontSizeFactoryDefaultsToTenWhenEmpty() {
        TextStyle style = FontSizeStyleFactory.getShared()
                .createStyle(null, new String[] {});
        assertEquals(10f, ((Number) style.get(TextAttribute.SIZE)).floatValue(), 0.001f);
    }

    @Test
    void fontSizeFactoryLeavesSizeUnsetForUnparseable() {
        TextStyle style = FontSizeStyleFactory.getShared()
                .createStyle(null, new String[] {"notanumber"});
        assertNull(style.get(TextAttribute.SIZE));
    }

    // ---- TrackingStyleFactory ---------------------------------------

    @Test
    void trackingFactoryParsesNumericValue() {
        TextStyle style = TrackingStyleFactory.getShared()
                .createStyle(null, new String[] {"0.15"});
        assertEquals(0.15f, ((Number) style.get(TextAttribute.TRACKING)).floatValue(), 0.001f);
    }

    @Test
    void trackingFactoryDefaultsToZero() {
        TextStyle style = TrackingStyleFactory.getShared()
                .createStyle(null, new String[] {});
        assertEquals(0f, ((Number) style.get(TextAttribute.TRACKING)).floatValue(), 0.001f);
    }

    @Test
    void trackingFactoryHandlesUnparseable() {
        TextStyle style = TrackingStyleFactory.getShared()
                .createStyle(null, new String[] {"bad"});
        // Unparseable falls back to 0.
        assertEquals(0f, ((Number) style.get(TextAttribute.TRACKING)).floatValue(), 0.001f);
    }

    // ---- WidthStyleFactory ------------------------------------------

    @Test
    void widthFactoryRecognizesStandardNames() {
        for (String name : new String[] {
                "regular", "condensed", "extended",
                "semicondensed", "semiextended"}) {
            TextStyle style = WidthStyleFactory.getShared()
                    .createStyle(null, new String[] {name});
            assertNotNull(style.get(TextAttribute.WIDTH),
                    "expected WIDTH attribute for name=" + name);
        }
    }

    @Test
    void widthFactoryAcceptsNumericInRange() {
        TextStyle style = WidthStyleFactory.getShared()
                .createStyle(null, new String[] {"1.2"});
        assertEquals(1.2f, ((Number) style.get(TextAttribute.WIDTH)).floatValue(), 0.001f);
    }

    @Test
    void widthFactoryRejectsNumericOutsideRange() {
        // Values <= 0 or > 100 are rejected; defaults to WIDTH_REGULAR.
        TextStyle style = WidthStyleFactory.getShared()
                .createStyle(null, new String[] {"-1"});
        assertEquals(TextAttribute.WIDTH_REGULAR, style.get(TextAttribute.WIDTH));

        style = WidthStyleFactory.getShared()
                .createStyle(null, new String[] {"200"});
        assertEquals(TextAttribute.WIDTH_REGULAR, style.get(TextAttribute.WIDTH));
    }

    @Test
    void widthFactoryDefaultsToRegularWhenEmpty() {
        TextStyle style = WidthStyleFactory.getShared()
                .createStyle(null, new String[] {});
        assertEquals(TextAttribute.WIDTH_REGULAR, style.get(TextAttribute.WIDTH));
    }

    @Test
    void widthFactoryUnknownNameDefaultsToRegular() {
        TextStyle style = WidthStyleFactory.getShared()
                .createStyle(null, new String[] {"squashed"});
        assertEquals(TextAttribute.WIDTH_REGULAR, style.get(TextAttribute.WIDTH));
    }

    // ---- WeightStyleFactory -----------------------------------------

    @Test
    void weightFactoryRecognizesStandardNames() {
        for (String name : new String[] {
                "regular", "bold", "light", "extralight", "demilight",
                "semibold", "medium", "demibold", "heavy",
                "extrabold", "ultrabold"}) {
            TextStyle style = WeightStyleFactory.getShared()
                    .createStyle(null, new String[] {name});
            assertNotNull(style.get(TextAttribute.WEIGHT),
                    "expected WEIGHT attribute for name=" + name);
        }
    }

    @Test
    void weightFactoryAcceptsNumericInRange() {
        TextStyle style = WeightStyleFactory.getShared()
                .createStyle(null, new String[] {"1.5"});
        assertEquals(1.5f, ((Number) style.get(TextAttribute.WEIGHT)).floatValue(), 0.001f);
    }

    @Test
    void weightFactoryRejectsNumericOutsideRange() {
        // Values <= 0 or > 100 are rejected; defaults to the
        // factory's initial value. WeightStyleFactory has a known
        // copy-paste bug where its default is WIDTH_REGULAR rather
        // than WEIGHT_REGULAR; both are 1.0 floats so the effect is
        // harmless, but pin the actual default behavior.
        TextStyle style = WeightStyleFactory.getShared()
                .createStyle(null, new String[] {"-1"});
        Float defaultValue = (Float) style.get(TextAttribute.WEIGHT);
        assertEquals(1.0f, defaultValue, 0.001f,
                "weight default should be 1.0 (WEIGHT_REGULAR / WIDTH_REGULAR equivalent)");

        style = WeightStyleFactory.getShared()
                .createStyle(null, new String[] {"200"});
        defaultValue = (Float) style.get(TextAttribute.WEIGHT);
        assertEquals(1.0f, defaultValue, 0.001f);
    }

    @Test
    void weightFactoryUnknownNameDefaultsToRegular() {
        TextStyle style = WeightStyleFactory.getShared()
                .createStyle(null, new String[] {"chunky"});
        assertEquals(1.0f, ((Number) style.get(TextAttribute.WEIGHT)).floatValue(), 0.001f);
    }

    @Test
    void weightFactoryBoldMapsToTextAttributeWeightBold() {
        TextStyle style = WeightStyleFactory.getShared()
                .createStyle(null, new String[] {"bold"});
        assertEquals(TextAttribute.WEIGHT_BOLD, style.get(TextAttribute.WEIGHT));
    }

    // ---- FontFamilyStyleFactory -------------------------------------

    @Test
    void fontFamilyFactoryStyleCarriesFamilyAttribute() {
        TextStyle style = FontFamilyStyleFactory.getShared()
                .createStyle(null, new String[] {"Monospaced"});
        assertTrue(style.contains(TextAttribute.FAMILY));
        // ResourceKit normalizes the name; for the standard "Monospaced"
        // family it should pass through unchanged.
        assertEquals("Monospaced", style.get(TextAttribute.FAMILY));
    }

    @Test
    void fontFamilyFactoryHandlesEmptyParameters() {
        // Factory uses "" as the family when no parameters are given;
        // ResourceKit.normalizeFontFamilyName handles the empty input.
        TextStyle style = FontFamilyStyleFactory.getShared()
                .createStyle(null, new String[] {});
        assertTrue(style.contains(TextAttribute.FAMILY));
    }

    // ---- Shared instances -------------------------------------------

    @Test
    void sharedInstancesAreSingletons() {
        // All factories expose a shared singleton via getShared().
        assertSame(ForegroundColorStyleFactory.getShared(),
                ForegroundColorStyleFactory.getShared());
        assertSame(BackgroundColorStyleFactory.getShared(),
                BackgroundColorStyleFactory.getShared());
        assertSame(FontSizeStyleFactory.getShared(),
                FontSizeStyleFactory.getShared());
        assertSame(FontFamilyStyleFactory.getShared(),
                FontFamilyStyleFactory.getShared());
        assertSame(TrackingStyleFactory.getShared(),
                TrackingStyleFactory.getShared());
        assertSame(WidthStyleFactory.getShared(),
                WidthStyleFactory.getShared());
        assertSame(WeightStyleFactory.getShared(),
                WeightStyleFactory.getShared());
    }
}
