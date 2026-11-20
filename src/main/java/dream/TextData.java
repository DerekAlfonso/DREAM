package dream;

import java.awt.Color;
import java.awt.Font;

/**
 * One line of text on screen.
 *
 * <p>A line keeps its text in full, how much of that text is currently revealed,
 * and optional per-character styling. Holding the full text and revealing a
 * prefix is what lets the typing animation crawl through a line that has
 * highlighted words in it: the highlight is attached to character ranges, so it
 * needs no work on each keystroke.
 *
 * <p>Only ever mutated on the Swing event thread, which is also the thread that
 * paints it, so no extra locking is needed.
 */
public class TextData {

    public String text;
    public int x;
    public int y;
    public int size;
    public Color color;
    public Font font;

    /** Colour of each character, or null when the whole line is one colour. */
    public Color[] charColors;

    /** Bold flag for each character, or null when nothing on the line is bold. */
    public boolean[] charBold;

    /** How many characters of {@link #text} are on screen. */
    public int revealed;

    public TextData(String text, int x, int y, int size, Color color, Font font) {
        this.text = text == null ? "" : text;
        this.x = x;
        this.y = y;
        this.size = size;
        this.color = color;
        this.font = font;
        this.revealed = this.text.length();
    }

    /** Replaces the line with unstyled text, all of it visible. */
    public void setText(String text) {
        this.text = text == null ? "" : text;
        this.revealed = this.text.length();
    }

    /** Shows the first {@code count} characters, which is how typing animates. */
    public void reveal(int count) {
        this.revealed = Math.max(0, Math.min(count, text.length()));
    }

    public void revealAll() {
        this.revealed = text.length();
    }

    public int visibleLength() {
        return Math.min(revealed, text.length());
    }

    public void setY(int y) {
        this.y = y;
    }

    public Color colorAt(int index) {
        if (charColors == null || index >= charColors.length || charColors[index] == null) {
            return color;
        }
        return charColors[index];
    }

    public boolean isBold(int index) {
        return charBold != null && index < charBold.length && charBold[index];
    }

    public boolean hasBold() {
        return charBold != null;
    }

    /** The same face in bold, derived on demand so a resize cannot leave it stale. */
    public Font boldFont() {
        return font.deriveFont(Font.BOLD);
    }
}
