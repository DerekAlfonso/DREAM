package dream;

import java.awt.Color;
import java.util.List;

/**
 * How a run of unreadable text misbehaves while it is on screen.
 *
 * <p>The defaults are a green-phosphor corruption: a small pool of blocky
 * glyphs, irregular pauses, and a fast burst that eases into a slower twitch,
 * so the effect never settles into an obvious loop and never looks like it
 * simply stopped either. Every value can be replaced through
 * {@link TextSpan#glitch(String, GlitchStyle)}.
 *
 * @param glyphs     characters the obscured text is replaced with
 * @param colors     colours a scrambled character can take
 * @param minStepMs  shortest pause between changes
 * @param maxStepMs  longest pause between changes
 * @param durationMs how long the run churns at full speed before it eases into
 *                   a slower twitch; it keeps twitching for as long as the line
 *                   is in the picture, so it never freezes. Zero means scramble
 *                   once and never animate at all
 * @param minDensity smallest share of the run re-rolled in one step, 0..1
 * @param maxDensity largest share of the run re-rolled in one step, 0..1
 * @param keepSpaces keep the original spaces, so the text keeps its word shape
 */
public record GlitchStyle(
        String glyphs,
        List<Color> colors,
        int minStepMs,
        int maxStepMs,
        int durationMs,
        double minDensity,
        double maxDensity,
        boolean keepSpaces) {

    private static final String DEFAULT_GLYPHS = "#%&@$?/\\|_=+*:.-";

    private static final List<Color> DEFAULT_COLORS = List.of(
        new Color(70, 190, 70),
        new Color(110, 235, 110),
        new Color(190, 255, 190),
        new Color(45, 120, 45));

    /** The stock corruption: about five seconds of irregular green noise. */
    public static final GlitchStyle DEFAULT = new GlitchStyle(
        DEFAULT_GLYPHS, DEFAULT_COLORS, 25, 90, 5200, 0.18, 0.55, true);

    public GlitchStyle {
        glyphs = glyphs == null ? "" : glyphs;
        if (glyphs.isEmpty()) {
            glyphs = DEFAULT_GLYPHS;
        }
        colors = colors == null || colors.isEmpty() ? DEFAULT_COLORS : List.copyOf(colors);
        minStepMs = Math.max(16, minStepMs);
        maxStepMs = Math.max(minStepMs, maxStepMs);
        durationMs = Math.max(0, durationMs);
        minDensity = Math.max(0.0, Math.min(1.0, minDensity));
        maxDensity = Math.max(minDensity, Math.min(1.0, maxDensity));
    }

    /** A style that keeps the default timing but uses one colour. */
    public static GlitchStyle of(Color color) {
        return new GlitchStyle(DEFAULT.glyphs(),
            List.of(color == null ? Color.GREEN : color), DEFAULT.minStepMs(),
            DEFAULT.maxStepMs(), DEFAULT.durationMs(), DEFAULT.minDensity(),
            DEFAULT.maxDensity(), DEFAULT.keepSpaces());
    }
}
