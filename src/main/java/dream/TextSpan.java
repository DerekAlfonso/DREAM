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
        return splitting(text, needles(passage), run -> glitch(run, style));
    }

    /**
     * Splits text into runs so every whole-word, case-insensitive occurrence of
     * the given word is highlighted. This is what points at the name of an
     * object from inside the sentence that describes it.
     */
    public static List<TextSpan> highlighting(String text, String word, Color color) {
        return highlighting(text, color, word);
    }

    /**
     * The same, for a line that names more than one thing worth pointing at, so
     * a description can pick out every part of it the player can act on.
     */
    public static List<TextSpan> highlighting(String text, Color color, String... words) {
        return splitting(text, needles(words), run -> highlight(run, color));
    }

    /**
     * The same, for a line that already carries styling: every plain run is
     * marked, while coloured, bold and glitched runs are left exactly as they
     * were written, so a scrambled passage stays unreadable.
     */
    public static List<TextSpan> highlighting(List<TextSpan> spans, Color color,
            String... words) {
        List<TextSpan> marked = new ArrayList<>();
        for (TextSpan span : spans) {
            boolean plain = span.color() == null && !span.bold() && span.glitch() == null;
            if (plain) {
                marked.addAll(highlighting(span.text(), color, words));
            } else {
                marked.add(span);
            }
        }
        return marked;
    }

    /** Lowercased, trimmed passages, with blanks dropped so none matches everything. */
    private static List<String> needles(String... passages) {
        List<String> needles = new ArrayList<>();
        for (String passage : passages) {
            String needle = passage == null ? "" : passage.trim().toLowerCase();
            if (!needle.isEmpty()) {
                needles.add(needle);
            }
        }
        return needles;
    }

    /**
     * Cuts text into plain runs around every whole-word occurrence of any of the
     * given passages, styling those occurrences with the supplied span factory.
     * The earliest occurrence wins, so overlapping passages do not double up. A
     * passage that never occurs leaves the whole line plain.
     */
    private static List<TextSpan> splitting(String text, List<String> needles,
            Function<String, TextSpan> marked) {
        List<TextSpan> spans = new ArrayList<>();
        if (needles.isEmpty()) {
            spans.add(of(text));
            return spans;
        }

        String haystack = text.toLowerCase();
        int kept = 0;
        int scan = 0;
        while (scan < haystack.length()) {
            int at = -1;
            int length = 0;
            for (String needle : needles) {
                int found = nextWholeWord(haystack, needle, scan);
                if (found >= 0 && (at < 0 || found < at)) {
                    at = found;
                    length = needle.length();
                }
            }
            if (at < 0) {
                break;
            }
            if (at > kept) {
                spans.add(of(text.substring(kept, at)));
            }
            spans.add(marked.apply(text.substring(at, at + length)));
            kept = at + length;
            scan = kept;
        }

        if (kept < text.length()) {
            spans.add(of(text.substring(kept)));
        }
        return spans;
    }

    /** The next whole-word occurrence of the needle at or after from, or -1. */
    private static int nextWholeWord(String haystack, String needle, int from) {
        int at = haystack.indexOf(needle, from);
        while (at >= 0 && !isWholeWord(haystack, at, at + needle.length())) {
            at = haystack.indexOf(needle, at + 1);
        }
        return at;
    }

    private static boolean isWholeWord(String text, int start, int end) {
        boolean openStart = start == 0 || !Character.isLetterOrDigit(text.charAt(start - 1));
        boolean closedEnd = end == text.length() || !Character.isLetterOrDigit(text.charAt(end));
        return openStart && closedEnd;
    }
}
