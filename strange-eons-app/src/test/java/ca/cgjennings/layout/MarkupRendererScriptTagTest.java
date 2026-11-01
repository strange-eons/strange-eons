package ca.cgjennings.layout;

import java.awt.Graphics2D;
import java.awt.Font;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.text.AttributedCharacterIterator;
import java.text.CharacterIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Covers the {@code <script>} and {@code <eval>} tags using a stub evaluator
 * in place of the script system.
 */
class MarkupRendererScriptTagTest {

    private EvaluatorFactory originalFactory;
    private final List<String[]> scriptCalls = new ArrayList<>();
    private final List<String> expressions = new ArrayList<>();
    private final List<MarkupRenderer> factoryCalls = new ArrayList<>();
    private Function<Object, Object> result = call -> call;

    @BeforeEach
    void installStubEvaluator() {
        originalFactory = MarkupRenderer.getEvaluatorFactory();
        MarkupRenderer.setEvaluatorFactory(renderer -> {
            factoryCalls.add(renderer);
            return new Evaluator() {
                @Override
                public Object evaluateScript(String[] params) {
                    scriptCalls.add(params);
                    return result.apply(params[0]);
                }

                @Override
                public Object evaluateExpression(String expression) {
                    expressions.add(expression);
                    return result.apply(expression);
                }
            };
        });
    }

    @AfterEach
    void restoreFactory() {
        MarkupRenderer.setEvaluatorFactory(originalFactory);
    }

    @Test
    void scriptReceivesAllParametersAndResultReplacesTag() {
        assertThat(parsedText("x<script Lib a \"b c\">y")).isEqualTo("xLiby");
        assertThat(scriptCalls).hasSize(1);
        assertThat(scriptCalls.get(0)).containsExactly("Lib", "a", "b c");
    }

    @Test
    void evalJoinsParametersWithSpaces() {
        assertThat(parsedText("x<eval 1 +  2>y")).isEqualTo("x1 + 2y");
        assertThat(expressions).containsExactly("1 + 2");
    }

    @Test
    void evalKeepsQuotedExpressionIntact() {
        parsedText("<eval \"a > b\">");
        assertThat(expressions).containsExactly("a > b");
    }

    @Test
    void tagsAreCaseInsensitiveAndKeepParameterCase() {
        parsedText("<SCRIPT Lib><EVAL Expr>");
        assertThat(scriptCalls.get(0)).containsExactly("Lib");
        assertThat(expressions).containsExactly("Expr");
    }

    @Test
    void tagsWithoutParametersAreDroppedWithoutEvaluating() {
        assertThat(parsedText("x<script>y<eval>z")).isEqualTo("xyz");
        assertThat(factoryCalls).isEmpty();
    }

    @Test
    void closeTagsAreIgnored() {
        assertThat(parsedText("x</script>y</eval>z")).isEqualTo("xyz");
        assertThat(factoryCalls).isEmpty();
    }

    @Test
    void nullResultIsEmpty() {
        result = call -> null;
        assertThat(parsedText("x<script a>y<eval b>z")).isEqualTo("xyz");
    }

    @Test
    void nonStringResultUsesToString() {
        result = call -> 42;
        assertThat(parsedText("<script a>,<eval b>")).isEqualTo("42,42");
    }

    @Test
    void resultMarkupIsParsed() {
        result = call -> "<b>x</b>";
        assertThat(parsedText("<eval a>")).isEqualTo("x");
        assertThat(fontsRendered("<eval a>").get(0).isBold()).isTrue();
    }

    @Test
    void resultCanDefineTagsForLaterMarkup() {
        result = call -> "<define later x>";
        assertThat(parsedText("<eval a><later>")).isEqualTo("x");
    }

    @Test
    void definitionShadowsScriptTags() {
        assertThat(parsedText("<define eval x><eval a>")).isEqualTo("x");
        assertThat(expressions).isEmpty();
    }

    @Test
    void evaluatorIsCreatedOncePerRendererWithThatRenderer() {
        MarkupRenderer r = new MarkupRenderer();
        parsedText(r, "<eval a><script b>");
        parsedText(r, "<eval c>");
        assertThat(factoryCalls).containsExactly(r);
    }

    @Test
    void eachRendererGetsItsOwnEvaluator() {
        parsedText("<eval a>");
        parsedText("<eval a>");
        assertThat(factoryCalls).hasSize(2);
    }

    @Test
    void layoutOfUnchangedMarkupDoesNotEvaluateAgain() {
        MarkupRenderer r = new MarkupRenderer();
        parsedText(r, "<eval a>");
        parsedText(r, "<eval a>");
        assertThat(expressions).hasSize(1);
    }

    @Test
    void changingATagTableEvaluatesAgain() {
        MarkupRenderer r = new MarkupRenderer();
        parsedText(r, "<eval a>");
        r.removeTag("b");
        parsedText(r, "<eval a>");
        assertThat(expressions).hasSize(2);
    }

    @Test
    void resultThatEvaluatesItselfShowsOverflowMessage() {
        result = call -> "<eval a>";
        assertThat(parsedText("<eval a>")).startsWith("Probable infinite recursion");
    }

    @Test
    void growingResultShowsOverflowMessage() {
        result = call -> "x<eval a>";
        MarkupRenderer r = new MarkupRenderer();
        r.setExpansionLimit(100);
        assertThat(parsedText(r, "<eval a>")).startsWith("Probable infinite recursion");
    }

    @Test
    @Disabled("BUG: only StackOverflowError is caught while parsing, so an exception "
            + "from the evaluator escapes through measure and draw, and no text renders.")
    void throwingEvaluatorShouldNotBreakLayout() {
        result = call -> {
            throw new IllegalStateException("script failed");
        };
        assertThatCode(() -> parsedText("x<eval a>y")).doesNotThrowAnyException();
    }

    @Test
    @Disabled("BUG: evaluatorFactory defaults to null and is only set during application "
            + "startup, so <script> or <eval> without one throws NullPointerException.")
    void missingFactoryShouldNotBreakLayout() {
        MarkupRenderer.setEvaluatorFactory(null);
        assertThatCode(() -> parsedText("x<eval a>y")).doesNotThrowAnyException();
    }

    private static String parsedText(String markup) {
        return parsedText(new MarkupRenderer(), markup);
    }

    private static String parsedText(MarkupRenderer r, String markup) {
        r.setMarkupText(markup);
        Graphics2D g = new BufferedImage(400, 200, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
            r.measure(g, new Rectangle2D.Double(0, 0, 300, 100));
        } finally {
            g.dispose();
        }
        StringBuilder b = new StringBuilder();
        for (MarkupRenderer.StyledParagraph p : r.getStyledText()) {
            AttributedCharacterIterator it = p.getIterator();
            for (char c = it.first(); c != CharacterIterator.DONE; c = it.next()) {
                b.append(c);
            }
        }
        return b.toString();
    }

    private static List<Font> fontsRendered(String markup) {
        MarkupRenderer r = new MarkupRenderer();
        r.setMarkupText(markup);
        Graphics2D spy = spy(new BufferedImage(400, 200, BufferedImage.TYPE_INT_ARGB).createGraphics());
        try {
            r.draw(spy, new Rectangle2D.Double(0, 0, 300, 100));
            ArgumentCaptor<GlyphVector> gv = ArgumentCaptor.forClass(GlyphVector.class);
            verify(spy, atLeast(1)).drawGlyphVector(gv.capture(), anyFloat(), anyFloat());
            return gv.getAllValues().stream().map(GlyphVector::getFont).toList();
        } finally {
            spy.dispose();
        }
    }
}
