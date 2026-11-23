package dream;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * One run of a printed line that carries its own style, so a single word can be
 * highlighted without restyling the rest of the sentence.
 *
 * <p>A null colour inherits the colour the line was printed in, which is what
 * every ordinary run uses; a span only sets a colour when it is the thing being
 * pointed out. A span with a {@link GlitchStyle} is scrambled instead of shown,
 * which hides text the player is never meant to read.
 */
public record TextSpan(String text, Color color, boolean bold, GlitchStyle glitch) {

    /** A run with no style of its own. */
    public static TextSpan of(String text) {
        return new TextSpan(text, null, false, null);
    }

    /** A run that stands out: its own colour, and bold so it reads as a label. */
    public static TextSpan highlight(String text, Color color) {
        return new TextSpan(text, color, true, null);
    }

    /**
     * A run that is never readable: it is scrambled before it is drawn and then
     * keeps changing according to the style.
     */
    public static TextSpan glitch(String text) {
        return glitch(text, GlitchStyle.DEFAULT);
    }

    /** The same, with a caller-supplied look and rhythm. */
    public static TextSpan glitch(String text, GlitchStyle style) {
        return new TextSpan(text, null, false,
            style == null ? GlitchStyle.DEFAULT : style);
    }

    /**
     * Splits a finished sentence into runs so every whole-word, case-insensitive
     * occurrence of the given passage is scrambled instead of read.
     *
     * <p>This is the glitched counterpart of {@link #highlighting}: the caller
     * writes the line once and names the part of it that is never readable,
     * rather than appending unreadable runs to the end of the message.
     */
    public static List<TextSpan> glitching(String text, String passage) {
        return glitching(text, passage, GlitchStyle.DEFAULT);
    }

    /** The same, with a caller-supplied look and rhythm. */
    public static List<TextSpan> glitching(String text, String passage, GlitchStyle style) {
        return splitting(text, passage, run -> glitch(run, style));
    }

    /**
     * Splits text into runs so every whole-word, case-insensitive occurrence of
     * the given word is highlighted. This is what points at the name of an
     * object from inside the sentence that describes it.
     */
    public static List<TextSpan> highlighting(String text, String word, Color color) {
        return splitting(text, word, run -> highlight(run, color));
    }

    /**
     * Cuts text into plain runs around every whole-word occurrence of the given
     * passage, styling those occurrences with the supplied span factory. A
     * passage that never occurs leaves the whole line plain.
     */
    private static List<TextSpan> splitting(String text, String passage,
            Function<String, TextSpan> marked) {
        List<TextSpan> spans = new ArrayList<>();
        String needle = passage == null ? "" : passage.trim().toLowerCase();
        if (needle.isEmpty()) {
            spans.add(of(text));
            return spans;
        }

        String haystack = text.toLowerCase();
        int kept = 0;
        int scan = 0;
        while (scan < haystack.length()) {
            int at = haystack.indexOf(needle, scan);
            if (at < 0) {
                break;
            }

            int end = at + needle.length();
            if (isWholeWord(text, at, end)) {
                if (at > kept) {
                    spans.add(of(text.substring(kept, at)));
                }
                spans.add(marked.apply(text.substring(at, end)));
                kept = end;
                scan = end;
            } else {
                // Part of a longer word, so keep looking past this one.
                scan = at + 1;
            }
        }

        if (kept < text.length()) {
            spans.add(of(text.substring(kept)));
        }
        return spans;
    }

    private static boolean isWholeWord(String text, int start, int end) {
        boolean openStart = start == 0 || !Character.isLetterOrDigit(text.charAt(start - 1));
        boolean closedEnd = end == text.length() || !Character.isLetterOrDigit(text.charAt(end));
        return openStart && closedEnd;
    }
}
