package dream;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * One run of text that keeps rewriting itself into unreadable glyphs.
 *
 * <p>Every marked character is scrambled once before the line is first drawn,
 * so the real wording is never visible, not even for a frame. The clock starts
 * with {@link #start(long)}, which the terminal calls once the whole line is on
 * screen, so slow typing cannot eat the animation before the passage is seen.
 * From there a random share of the run changes at irregular intervals.
 *
 * <p>It never freezes while the line is still in the log: the style's duration
 * is how long it churns at full speed, and after that it eases into an
 * occasional skew, spaced irregularly, so the passage never reads as a still
 * image that simply stopped moving.
 *
 * <p>A run is parked while its line is scrolled out of the picture: it holds
 * both its frame and its clock, so nothing is spent on text nobody can see and
 * it carries on where it left off when it comes back. The burst is therefore
 * measured in time the line is actually visible.
 *
 * <p>The animation is deliberately erratic rather than periodic: each step has
 * a different density, skips some characters, and waits a different amount of
 * time, so it does not read as a loop. Only touched on the Swing event thread.
 */
final class TextGlitch {

    private final TextData line;
    private final int[] masked;
    private final GlitchStyle[] styles;
    private final boolean[] keepSpace;
    private final GlitchStyle timing;
    private final Random random = new Random();
    private final char[] chars;
    private final Color[] colors;

    /** Set by {@link #start(long)}; until then the run is frozen on its first frame. */
    private boolean started;
    private long burstEndsAt;
    private long nextStepAt;

    /** When the line left the picture; zero while it is on screen. */
    private long pausedAt;

    private TextGlitch(TextData line, int[] masked, GlitchStyle[] styles,
            boolean[] keepSpace, GlitchStyle timing) {
        this.line = line;
        this.masked = masked;
        this.styles = styles;
        this.keepSpace = keepSpace;
        this.timing = timing;
        this.chars = line.text.toCharArray();
        this.colors = line.charColors != null && line.charColors.length == chars.length
            ? line.charColors
            : new Color[chars.length];
        line.charColors = colors;
    }

    /**
     * Scrambles the marked characters and returns the run frozen on that first
     * frame, or null when no character on the line is marked.
     */
    static TextGlitch attach(TextData line, GlitchStyle[] styles) {
        if (styles == null) {
            return null;
        }
        int length = Math.min(styles.length, line.text.length());
        int[] marked = new int[length];
        boolean[] keepSpace = new boolean[length];
        GlitchStyle timing = null;
        int count = 0;
        for (int i = 0; i < length; i++) {
            GlitchStyle style = styles[i];
            if (style == null) {
                continue;
            }
            marked[count++] = i;
            keepSpace[i] = style.keepSpaces() && line.text.charAt(i) == ' ';
            if (timing == null) {
                timing = style;
            }
        }
        if (count == 0) {
            return null;
        }
        TextGlitch glitch = new TextGlitch(line, Arrays.copyOf(marked, count),
            styles, keepSpace, timing);
        glitch.scrambleEverything();
        return glitch;
    }

    TextData line() {
        return line;
    }

    /** True once {@link #start(long)} has armed the run. */
    boolean isStarted() {
        return started;
    }

    /**
     * Starts the clock. Called once the line is fully revealed, so a slow crawl
     * does not spend the whole animation on characters nobody has reached yet.
     * Does nothing on later calls, so a skipped or re-revealed line cannot
     * restart or extend it.
     */
    void start(long now) {
        if (started) {
            return;
        }
        started = true;
        burstEndsAt = now + timing.durationMs();
        nextStepAt = now + nextDelay(now);
    }

    /** False when the style asked for one scrambled frame and nothing more. */
    boolean animates() {
        return timing.durationMs() > 0;
    }

    /** True when an armed run has reached its next change. */
    boolean isDue(long now) {
        return started && now >= nextStepAt;
    }

    /**
     * Parks the run while its line is outside the picture, so an off-screen
     * passage costs no steps and no repaints. Repeat calls are ignored.
     */
    void pause(long now) {
        if (started && pausedAt == 0L) {
            pausedAt = now;
        }
    }

    /**
     * Picks the clock back up after {@link #pause(long)}, moved forward by the
     * time spent off screen, so a run scrolled back into view resumes its burst
     * instead of skipping to the twitching tail.
     */
    void resume(long now) {
        if (pausedAt == 0L) {
            return;
        }
        long waited = now - pausedAt;
        pausedAt = 0L;
        burstEndsAt += waited;
        nextStepAt += waited;
    }

    /** Re-rolls a random share of the run. Runs on the event thread. */
    void step(long now) {
        double spread = timing.maxDensity() - timing.minDensity();
        double share = timing.minDensity() + random.nextDouble() * spread;
        // Every so often the corruption surges, so the rhythm never settles.
        if (random.nextInt(7) == 0) {
            share = Math.min(1.0, share + 0.25);
        }
        int changes = Math.max(1, (int) Math.round(masked.length * share));
        for (int n = 0; n < changes; n++) {
            int index = masked[random.nextInt(masked.length)];
            // Leave a few characters untouched, so it does not look like
            // uniform noise or a fixed pattern being replayed.
            if (random.nextInt(7) == 0) {
                continue;
            }
            GlitchStyle style = styles[index] != null ? styles[index] : timing;
            rewrite(index, style);
        }
        line.text = new String(chars);
        nextStepAt = now + nextDelay(now);
    }

    private void scrambleEverything() {
        for (int index : masked) {
            GlitchStyle style = styles[index] != null ? styles[index] : timing;
            rewrite(index, style);
        }
        line.text = new String(chars);
    }

    private void rewrite(int index, GlitchStyle style) {
        if (keepSpace[index]) {
            return;
        }
        String glyphs = style.glyphs();
        chars[index] = glyphs.charAt(random.nextInt(glyphs.length()));
        List<Color> palette = style.colors();
        colors[index] = palette.get(random.nextInt(palette.size()));
    }

    private int nextDelay(long now) {
        if (now >= burstEndsAt) {
            // Past the burst the run keeps twitching instead of freezing. The
            // pauses are long and never the same twice, so the page looks like
            // ink that will not settle rather than an effect that stopped.
            int base = timing.maxStepMs() * 8;
            return base + random.nextInt(base * 2 + 1);
        }
        int spread = timing.maxStepMs() - timing.minStepMs();
        int delay = timing.minStepMs() + (spread <= 0 ? 0 : random.nextInt(spread + 1));
        // A longer stutter now and then keeps the effect from ticking like a
        // clock.
        if (random.nextInt(10) == 0) {
            delay += timing.maxStepMs() * (1 + random.nextInt(3));
        }
        return delay;
    }
}
