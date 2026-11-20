package dream;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * One run of a printed line that carries its own style, so a single word can be
 * highlighted without restyling the rest of the sentence.
 *
 * <p>A null colour inherits the colour the line was printed in, which is what
 * every ordinary run uses; a span only sets a colour when it is the thing being
 * pointed out.
 */
public record TextSpan(String text, Color color, boolean bold) {

    /** A run with no style of its own. */
    public static TextSpan of(String text) {
        return new TextSpan(text, null, false);
    }

    /** A run that stands out: its own colour, and bold so it reads as a label. */
    public static TextSpan highlight(String text, Color color) {
        return new TextSpan(text, color, true);
    }

    /**
     * Splits text into runs so every whole-word, case-insensitive occurrence of
     * the given word is highlighted. This is what points at the name of an
     * object from inside the sentence that describes it.
     */
    public static List<TextSpan> highlighting(String text, String word, Color color) {
        List<TextSpan> spans = new ArrayList<>();
        String needle = word == null ? "" : word.trim().toLowerCase();
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
                spans.add(highlight(text.substring(at, end), color));
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
