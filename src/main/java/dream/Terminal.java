package dream;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

/**
 * The scrolling terminal: queues messages, types them out a character at a
 * time, wraps long lines and collects player input.
 *
 * <p>Threading rules, which the CodeHS version did not have and which caused
 * flicker and lost characters:
 * <ul>
 *   <li>Every field here is touched only on the Swing event thread.</li>
 *   <li>The story thread calls {@link #print} (returns immediately),
 *       {@link #awaitIdle} and {@link #prompt} (both block).</li>
 * </ul>
 */
public final class Terminal {

    // Layout is authored against this canvas height and scaled up from there,
    // so the terminal stays readable on a 4K panel instead of shrinking to
    // nothing. Everything below is a base value in "reference pixels".
    private static final int REFERENCE_HEIGHT = 900;
    private static final double MIN_AUTO_SCALE = 1.0;
    private static final double MAX_AUTO_SCALE = 3.5;

    /** Gap between separate messages. */
    private static final int BASE_LINE_HEIGHT = 40;

    /** Tighter gap for the overflow lines of a wrapped message. */
    private static final int BASE_WRAP_HEIGHT = 25;

    /** Distance from the bottom of the canvas where new lines appear. */
    private static final int BASE_HOME_MARGIN = 100;

    /** Closest the oldest line may be scrolled to the top of the screen. */
    private static final int BASE_TOP_MARGIN = 60;

    /**
     * Widest the readable text column is allowed to get, in reference pixels.
     * Roughly 85 characters, which keeps the column near the middle of a wide
     * screen instead of starting hard against the left edge.
     */
    private static final int BASE_MAX_COLUMN = 700;

    /** Shortest gap between two typing clicks, so fast text is not a buzz. */
    private static final int CLICK_INTERVAL_MS = 80;

    /** Lines kept in memory. Older ones are unreachable anyway once clamped. */
    private static final int MAX_RETAINED_LINES = 400;

    /** A print speed of zero reveals a whole line at once instead of typing it. */
    private static final int INSTANT_SPEED = 0;

    /** How often the shared glitch clock checks which runs are due to change. */
    private static final int GLITCH_TICK_MS = 25;

    private static CurveCanvas canvas;
    private static Font baseFont = new Font(Font.MONOSPACED, Font.PLAIN, 15);

    private static final List<TextData> activeLines = new ArrayList<>();
    private static final Queue<MessageRequest> messageQueue = new LinkedList<>();
    private static boolean isTyping = false;

    /** The line being typed, the request it came from, and its timer. */
    private static Timer typingTimer = null;
    private static TextData typingLine = null;
    private static MessageRequest typingRequest = null;

    /** Unreadable runs being tracked; one timer drives the ones in the picture. */
    private static final List<TextGlitch> glitches = new ArrayList<>();
    private static Timer glitchTimer = null;

    /** Shared by every wrapped line produced by one print() call. */
    private static int nextPrintId = 0;

    private static int scrollOffset = 0;
    private static long lastClickAt = 0L;

    /** Story threads parked in awaitIdle(), released when the queue drains. */
    private static final List<CountDownLatch> idleWaiters = new ArrayList<>();

    private static boolean awaitingInput = false;
    private static String currentInput = "";
    private static String promptSymbol = "> ";
    private static TextData inputLine = null;
    private static BlockingQueue<String> inputResult = null;

    private static double speedMultiplier = 1.0;
    private static boolean typingSounds = true;

    /** Set when Enter asks the current wait or print to finish early. */
    private static volatile boolean skipRequested = false;

    /** Caret position inside currentInput. */
    private static int inputCursor = 0;

    /** Previously submitted answers, oldest first, recalled with the arrows. */
    private static final List<String> inputHistory = new ArrayList<>();
    private static int historyIndex = 0;
    private static String inputDraft = "";
    private static final int MAX_HISTORY = 50;

    /** Drawn at the caret while the player is typing. */
    private static final String CARET = "|";

    /** How long the caret stays on, and then off, while a prompt waits. */
    private static final int CARET_BLINK_MS = 500;

    /** Flips on a timer while a prompt is waiting, so it is clear you can type. */
    private static Timer caretTimer = null;
    private static boolean caretVisible = true;

    private Terminal() { }

    private static final class MessageRequest {
        final String text;
        final Color[] charColors;
        final boolean[] charBold;
        final GlitchStyle[] charGlitch;
        final int speed;
        final int fontSize;
        final Color color;
        final int pushUpAmount;
        final Font font;
        final int printId;

        MessageRequest(String text, Color[] charColors, boolean[] charBold,
                       GlitchStyle[] charGlitch, int speed, int fontSize, Color color,
                       int pushUpAmount, Font font, int printId) {
            this.text = text;
            this.charColors = charColors;
            this.charBold = charBold;
            this.charGlitch = charGlitch;
            this.speed = speed;
            this.fontSize = fontSize;
            this.color = color;
            this.pushUpAmount = pushUpAmount;
            this.font = font;
            this.printId = printId;
        }
    }

    // ----------------------------------------------------------------- setup

    public static void attach(CurveCanvas target, Font font) {
        canvas = target;
        baseFont = font;
    }

    public static void setSpeedMultiplier(double multiplier) {
        speedMultiplier = Math.max(0.1, Math.min(5.0, multiplier));
    }

    public static double getSpeedMultiplier() {
        return speedMultiplier;
    }

    public static void setTypingSounds(boolean enabled) {
        typingSounds = enabled;
    }

    public static boolean isTypingSounds() {
        return typingSounds;
    }

    /**
     * Sleeps on the story thread, but returns early when the player presses
     * Enter. Story beats and faked loads both use this.
     */
    public static void pause(long millis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + Math.max(0L, millis);
        while (true) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                skipRequested = false;
                return;
            }
            if (skipRequested) {
                skipRequested = false;
                return;
            }
            Thread.sleep(Math.min(25L, remaining));
        }
    }

    // ---------------------------------------------------------------- layout

    /**
     * How much to enlarge everything for the current canvas, combining an
     * automatic factor derived from the screen height with the player's own
     * textScale preference.
     */
    private static double uiScale() {
        double auto = MIN_AUTO_SCALE;
        if (canvas != null && canvas.getViewHeight() > 0) {
            auto = canvas.getViewHeight() / (double) REFERENCE_HEIGHT;
            auto = Math.max(MIN_AUTO_SCALE, Math.min(MAX_AUTO_SCALE, auto));
        }
        return auto * Settings.textScale;
    }

    private static int scaled(int baseValue) {
        return (int) Math.round(baseValue * uiScale());
    }

    private static int scaledFontSize(int baseSize) {
        return Math.max(8, (int) Math.round(baseSize * uiScale()));
    }

    /**
     * Width of the readable text column. Capped so lines do not stretch across
     * an ultrawide display, which is unreadable and looks nothing like a
     * terminal.
     */
    private static int contentWidth() {
        int canvasWidth = canvas != null && canvas.getViewWidth() > 0
            ? canvas.getViewWidth() : 1280;
        int cap = scaled(BASE_MAX_COLUMN);
        int available = canvasWidth - scaled(80);
        return Math.max(320, Math.min(available, cap));
    }

    /** Left edge of the text column, centred within the canvas. */
    private static int startX() {
        int canvasWidth = canvas != null && canvas.getViewWidth() > 0
            ? canvas.getViewWidth() : 1280;
        return Math.max(scaled(20), (canvasWidth - contentWidth()) / 2);
    }

    /**
     * Recentres and rescales existing lines after the window changes size.
     * Called by the canvas on resize.
     */
    public static void relayout() {
        if (canvas == null) {
            return;
        }
        int x = startX();
        for (TextData line : activeLines) {
            line.x = x;
            line.font = baseFont.deriveFont(Font.PLAIN,
                (float) scaledFontSize(line.size));
        }
        canvas.repaint();
        syncGlitches();
    }

    // --------------------------------------------------------------- output

    /**
     * Queues a message. Returns immediately; the text types itself out on the
     * event thread. Long messages are wrapped to the canvas width first.
     *
     * @param speed milliseconds between characters
     */
    public static void print(String fullText, int speed, int fontSize, Color color) {
        print(List.of(TextSpan.of(fullText == null ? "" : fullText)),
            speed, fontSize, color);
    }

    /**
     * Queues a line built from styled runs, so a word or two can be highlighted
     * inside an otherwise uniform sentence. Wrapping, typing and skip-typing
     * treat it exactly like a plain line.
     *
     * @param speed milliseconds between characters
     */
    public static void print(List<TextSpan> spans, int speed, int fontSize, Color color) {
        if (canvas == null) {
            return;
        }
        final List<TextSpan> safeSpans = List.copyOf(spans);
        SwingUtilities.invokeLater(() -> enqueue(safeSpans, speed, fontSize, color));
    }

    /**
     * Queues a line that is on screen the moment it reaches the front of the
     * queue, with no per-character animation. Combat uses this so a fight reads
     * one line at a time instead of crawling out letter by letter.
     */
    public static void printInstant(String fullText, int fontSize, Color color) {
        printInstant(List.of(TextSpan.of(fullText == null ? "" : fullText)),
            fontSize, color);
    }

    /** The instant counterpart of {@link #print(List, int, int, Color)}. */
    public static void printInstant(List<TextSpan> spans, int fontSize, Color color) {
        if (canvas == null) {
            return;
        }
        final List<TextSpan> safeSpans = List.copyOf(spans);
        SwingUtilities.invokeLater(() -> queue(safeSpans, INSTANT_SPEED, fontSize, color));
    }

    /** Convenience for a blank spacer line. */
    public static void blankLine() {
        print(" ", 1, Game.getStandardSize(), Color.WHITE);
    }

    /** A dashed rule, for separating one part of the game from the next. */
    public static void divider() {
        print("----------------------", 1, Game.getStandardSize(), Color.WHITE);
    }

    /**
     * Adds a line that rewrites itself in place instead of typing out, for
     * things like a progress bar that has to update on one row.
     *
     * <p>Blocks until queued dialogue has finished so the line does not appear
     * in the middle of a sentence.
     */
    public static StatusLine openStatusLine(int fontSize, Color color) {
        awaitIdle();

        StatusLine handle = new StatusLine();
        SwingUtilities.invokeLater(() -> {
            for (TextData line : activeLines) {
                line.setY(line.y - scaled(BASE_LINE_HEIGHT));
            }

            Font font = baseFont.deriveFont(Font.PLAIN, (float) scaledFontSize(fontSize));
            TextData data = new TextData("", startX(), homeY(), fontSize, color, font);
            activeLines.add(data);
            canvas.addTextToLayer(CurveCanvas.TEXT_LAYER, data);
            trimHistory();
            handle.bind(data);
        });
        return handle;
    }

    /**
     * Draws a block of rows at once: no typing animation, no word wrap, and
     * line spacing tight enough that the rows read as one contiguous picture
     * rather than separate messages.
     *
     * <p>This is what doom.exe renders its ASCII viewport into. Ordinary
     * {@link #print} is useless for that, because it types characters out and
     * leaves a full message gap between every row.
     *
     * <p>Blocks until queued dialogue has finished, so a frame never lands in
     * the middle of a sentence.
     */
    public static void printFrame(String[] rows, int fontSize, Color color) {
        if (canvas == null || rows.length == 0) {
            return;
        }
        final String[] frame = rows.clone();
        awaitIdle();

        SwingUtilities.invokeLater(() -> {
            // Just over one glyph height, so rows touch without overlapping.
            int rowGap = Math.max(1, (int) Math.round(fontSize * 1.15 * uiScale()));

            // Clear enough room for the whole block plus a normal message gap.
            int push = (frame.length - 1) * rowGap + scaled(BASE_LINE_HEIGHT);
            for (TextData line : activeLines) {
                line.setY(line.y - push);
            }

            Font font = baseFont.deriveFont(Font.PLAIN, (float) scaledFontSize(fontSize));
            int x = startX();
            int bottom = homeY();

            for (int i = 0; i < frame.length; i++) {
                int y = bottom - (frame.length - 1 - i) * rowGap;
                TextData data = new TextData(frame[i], x, y, fontSize, color, font);
                activeLines.add(data);
                canvas.addTextToLayer(CurveCanvas.TEXT_LAYER, data);
            }

            trimHistory();
            canvas.repaint();
        });
    }

    /**
     * A handle to one rewritable line. Safe to call from the story thread: the
     * update is marshalled onto the event thread, and because that queue is
     * ordered it always runs after the line itself has been created.
     */
    public static final class StatusLine {
        private TextData data;

        private void bind(TextData data) {
            this.data = data;
        }

        /** Replaces the whole line. Keep the text a fixed width so it does not jitter. */
        public void set(String text) {
            SwingUtilities.invokeLater(() -> {
                if (data != null) {
                    data.setText(text);
                    canvas.repaint();
                }
            });
        }
    }

    private static void enqueue(List<TextSpan> spans, int speed, int fontSize, Color color) {
        queue(spans, Math.max(1, (int) Math.round(speed / speedMultiplier)),
            fontSize, color);
    }

    /**
     * Wraps styled runs to the column and queues them. A speed of
     * {@link #INSTANT_SPEED} puts the whole line up at once instead of typing it
     * out.
     *
     * <p>Each character keeps the style of the run it came from, and every
     * character is measured in the font it will actually be drawn in, so a bold
     * run wraps like the wider glyphs it puts on screen instead of like a
     * normal-weight copy of them.
     */
    private static void queue(List<TextSpan> spans, int speed, int fontSize, Color color) {
        Font font = baseFont.deriveFont(Font.PLAIN, (float) scaledFontSize(fontSize));
        FontMetrics plainMetrics = canvas.getFontMetrics(font);
        FontMetrics boldMetrics = canvas.getFontMetrics(font.deriveFont(Font.BOLD));

        StringBuilder builder = new StringBuilder();
        for (TextSpan span : spans) {
            builder.append(span.text() == null ? "" : span.text());
        }
        String plain = builder.toString();

        int[] advance = new int[plain.length()];
        Color[] charColors = new Color[plain.length()];
        boolean[] charBold = new boolean[plain.length()];
        GlitchStyle[] charGlitch = new GlitchStyle[plain.length()];

        int at = 0;
        for (TextSpan span : spans) {
            String runText = span.text() == null ? "" : span.text();
            FontMetrics runMetrics = span.bold() ? boldMetrics : plainMetrics;
            for (int i = 0; i < runText.length(); i++) {
                advance[at] = runMetrics.charWidth(runText.charAt(i));
                charColors[at] = span.color();
                charBold[at] = span.bold();
                charGlitch[at] = span.glitch();
                at++;
            }
        }

        int printId = ++nextPrintId;
        int lineGap = scaled(BASE_LINE_HEIGHT);
        int wrapGap = scaled(BASE_WRAP_HEIGHT);

        List<int[]> chunks = wrapChunks(plain, advance, contentWidth());
        for (int i = 0; i < chunks.size(); i++) {
            int start = chunks.get(i)[0];
            int end = chunks.get(i)[1];
            int gap = i == 0 ? lineGap : wrapGap;
            messageQueue.add(new MessageRequest(plain.substring(start, end),
                Arrays.copyOfRange(charColors, start, end),
                Arrays.copyOfRange(charBold, start, end),
                Arrays.copyOfRange(charGlitch, start, end),
                speed, fontSize, color, gap, font, printId));
        }

        processNextMessage();
    }

    /**
     * Splits text into the chunks that fit the column, given the advance of each
     * character. Breaks at the last space that fits and drops the spaces it
     * broke on; a single run of unbroken characters wider than the column is
     * split where it overflows rather than being left to run off the edge.
     *
     * @return start and end offsets into the text, in order
     */
    static List<int[]> wrapChunks(String text, int[] advance, int maxWidth) {
        List<int[]> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = start;
            int width = 0;
            int lastSpace = -1;
            while (end < text.length()) {
                if (end > start && width + advance[end] > maxWidth) {
                    break;
                }
                width += advance[end];
                if (text.charAt(end) == ' ') {
                    lastSpace = end;
                }
                end++;
            }

            boolean fitsWholeWord = end < text.length() && lastSpace > start;
            int breakAt = fitsWholeWord ? lastSpace : end;
            chunks.add(new int[] {start, breakAt});

            start = breakAt;
            while (start < text.length() && text.charAt(start) == ' ') {
                start++;
            }
        }
        return chunks;
    }

    /** Runs on the event thread. Types one queued line, then chains to the next. */
    private static void processNextMessage() {
        if (isTyping || messageQueue.isEmpty() || canvas == null) {
            return;
        }

        isTyping = true;
        MessageRequest req = messageQueue.poll();
        typingRequest = req;

        TextData newLine = addMessageLine(req);
        typingLine = newLine;

        if (req.speed == INSTANT_SPEED) {
            // Reveal, do not setText: an instant line may carry a glitched run
            // that has already replaced its real wording.
            newLine.revealAll();
            startGlitchesFor(newLine);
            canvas.repaint();
            finishMessage();
            return;
        }

        final int[] charIndex = {0};
        Timer timer = new Timer(req.speed, null);
        typingTimer = timer;
        timer.addActionListener(e -> {
            if (charIndex[0] < req.text.length()) {
                char c = req.text.charAt(charIndex[0]);
                charIndex[0]++;
                newLine.reveal(charIndex[0]);
                if (c != ' ') {
                    playTypingClick();
                }
                canvas.repaint();
            } else {
                timer.stop();
                // The line is finally all on screen, so an unreadable run on it
                // can begin moving now instead of hours before it was visible.
                startGlitchesFor(newLine);
                finishMessage();
            }
        });
        timer.setInitialDelay(req.speed);
        timer.start();
    }

    /**
     * Makes room for one message line and adds it empty. Shared by normal
     * typing and by the instant reveal, so both push the log up identically.
     */
    private static TextData addMessageLine(MessageRequest req) {
        for (TextData line : activeLines) {
            line.setY(line.y - req.pushUpAmount);
        }

        TextData newLine = new TextData(req.text, startX(), homeY(), req.fontSize,
            req.color, req.font);
        newLine.charColors = req.charColors;
        newLine.charBold = req.charBold;
        newLine.reveal(0);
        // Scramble before the line joins the layer, so its real wording is
        // never painted even for one frame. The run does not start moving until
        // the line is fully revealed (see startGlitchesFor).
        TextGlitch glitch = TextGlitch.attach(newLine, req.charGlitch);
        activeLines.add(newLine);
        canvas.addTextToLayer(CurveCanvas.TEXT_LAYER, newLine);
        if (glitch != null && glitch.animates()) {
            glitches.add(glitch);
        }
        trimHistory();
        return newLine;
    }

    /**
     * Starts the clock on every unreadable run belonging to a line that is now
     * fully revealed. Runs on the event thread.
     */
    private static void startGlitchesFor(TextData line) {
        long now = System.currentTimeMillis();
        for (TextGlitch glitch : glitches) {
            if (glitch.line() == line) {
                glitch.start(now);
            }
        }
        syncGlitches();
    }

    /** Runs on the event thread when a line has finished, normally or skipped. */
    private static void finishMessage() {
        typingTimer = null;
        typingLine = null;
        typingRequest = null;
        isTyping = false;

        if (messageQueue.isEmpty()) {
            releaseIdleWaiters();
        }
        processNextMessage();
    }

    /**
     * Reveals the rest of the print that is currently typing, including every
     * wrapped line belonging to it, then starts the next print normally.
     *
     * <p>Does nothing while a prompt is waiting: Enter there is the player's
     * answer, never a request to hurry the text along.
     */
    public static void skipTyping() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(Terminal::skipTyping);
            return;
        }
        if (awaitingInput || !isTyping || typingRequest == null) {
            return;
        }

        int printId = typingRequest.printId;
        if (typingTimer != null) {
            typingTimer.stop();
        }
        if (typingLine != null) {
            typingLine.revealAll();
            startGlitchesFor(typingLine);
        }

        while (!messageQueue.isEmpty()
            && messageQueue.peek().printId == printId) {
            MessageRequest rest = messageQueue.poll();
            TextData restLine = addMessageLine(rest);
            restLine.revealAll();
            startGlitchesFor(restLine);
        }

        canvas.repaint();
        finishMessage();
    }

    /** Rate-limited so a fast crawl does not turn into a solid buzz. */
    private static void playTypingClick() {
        if (!typingSounds) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastClickAt >= CLICK_INTERVAL_MS) {
            lastClickAt = now;
            AudioManager.playTypingClick();
        }
    }

    private static int homeY() {
        int height = canvas.getViewHeight() > 0 ? canvas.getViewHeight() : 800;
        return (height - scaled(BASE_HOME_MARGIN)) + scrollOffset;
    }

    private static void trimHistory() {
        if (activeLines.size() > MAX_RETAINED_LINES) {
            activeLines.subList(0, activeLines.size() - MAX_RETAINED_LINES).clear();
            canvas.pruneText(MAX_RETAINED_LINES);
            // Pruning can drop the line under a parked run, and no tick will be
            // along to clear it while everything else is off screen.
            glitches.removeIf(g -> !activeLines.contains(g.line()));
        }
    }

    /** True while the canvas would actually paint the line. */
    private static boolean isOnScreen(TextData line) {
        return canvas == null || canvas.paints(line);
    }

    private static void ensureGlitchTimer() {
        if (glitchTimer != null) {
            return;
        }
        glitchTimer = new Timer(GLITCH_TICK_MS, e -> tickGlitches());
        glitchTimer.setCoalesce(true);
        glitchTimer.start();
    }

    /**
     * Re-checks every armed run after something moved it: runs that left the
     * picture are parked where they stand, and the shared clock starts if one is
     * visible and may move. Without the parking here, a run scrolled away while
     * the clock was already stopped would spend its burst unseen.
     */
    private static void syncGlitches() {
        long now = System.currentTimeMillis();
        boolean anyVisible = false;
        for (TextGlitch glitch : glitches) {
            if (!glitch.isStarted()) {
                continue;
            }
            if (isOnScreen(glitch.line())) {
                anyVisible = true;
            } else {
                glitch.pause(now);
            }
        }
        if (anyVisible) {
            ensureGlitchTimer();
        }
    }

    /**
     * Shared animation tick. Each run decides its own irregular next step, so
     * this clock only wakes up often enough to notice when one is due.
     *
     * <p>Runs on text the canvas has scrolled out of the picture are parked and
     * the clock stops entirely while none of them is visible, so a passage left
     * in the scrollback stops costing frames.
     */
    private static void tickGlitches() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        boolean anyVisible = false;
        for (TextGlitch glitch : glitches) {
            if (!isOnScreen(glitch.line())) {
                glitch.pause(now);
                continue;
            }
            glitch.resume(now);
            if (!glitch.isStarted()) {
                continue;
            }
            anyVisible = true;
            if (glitch.isDue(now)) {
                glitch.step(now);
                changed = true;
            }
        }
        if (changed) {
            canvas.repaint();
        }
        if (!anyVisible) {
            stopGlitchClock();
        }
    }

    private static void stopGlitchClock() {
        if (glitchTimer != null) {
            glitchTimer.stop();
            glitchTimer = null;
        }
    }

    private static void stopGlitches() {
        glitches.clear();
        stopGlitchClock();
    }

    // ----------------------------------------------------------- idle waiting

    /**
     * Blocks the calling story thread until everything queued has finished
     * typing. Prevents a prompt from appearing in the middle of a sentence.
     */
    public static void awaitIdle() {
        if (SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("awaitIdle() would deadlock the event thread");
        }

        CountDownLatch latch = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            if (!isTyping && messageQueue.isEmpty()) {
                latch.countDown();
            } else {
                idleWaiters.add(latch);
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void releaseIdleWaiters() {
        for (CountDownLatch latch : idleWaiters) {
            latch.countDown();
        }
        idleWaiters.clear();
    }

    // ---------------------------------------------------------------- input

    /**
     * Shows the input caret and blocks the story thread until the player
     * presses Enter with a non-blank answer.
     *
     * @return exactly what they typed, never null
     */
    public static String prompt() {
        if (SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("prompt() would deadlock the event thread");
        }

        awaitIdle();

        BlockingQueue<String> result = new ArrayBlockingQueue<>(1);
        SwingUtilities.invokeLater(() -> beginInput(result));

        try {
            return result.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    private static void beginInput(BlockingQueue<String> result) {
        awaitingInput = true;
        currentInput = "";
        inputCursor = 0;
        historyIndex = inputHistory.size();
        inputDraft = "";
        skipRequested = false;
        inputResult = result;

        for (TextData line : activeLines) {
            line.setY(line.y - scaled(BASE_LINE_HEIGHT));
        }

        Font font = baseFont.deriveFont(Font.PLAIN, (float) scaledFontSize(15));
        caretVisible = true;
        inputLine = new TextData(promptSymbol + CARET, startX(), homeY(), 15, Color.GREEN, font);
        activeLines.add(inputLine);
        canvas.addTextToLayer(CurveCanvas.TEXT_LAYER, inputLine);
        startCaretBlink();
        canvas.repaint();
    }

    /** Blinks the caret so an idle prompt still reads as waiting for a typed answer. */
    private static void startCaretBlink() {
        stopCaretBlink();
        caretTimer = new Timer(CARET_BLINK_MS, e -> {
            caretVisible = !caretVisible;
            refreshInputLine();
        });
        caretTimer.start();
    }

    private static void stopCaretBlink() {
        if (caretTimer != null) {
            caretTimer.stop();
            caretTimer = null;
        }
    }

    /** Called from the key listener on the event thread. */
    public static void handleCharTyped(char c) {
        if (!awaitingInput || inputLine == null) {
            return;
        }
        if (c >= 32 && c <= 126) {
            currentInput = currentInput.substring(0, inputCursor)
                + c
                + currentInput.substring(inputCursor);
            inputCursor++;
            AudioManager.playTypingClick();
            refreshInputLine();
        }
    }

    /** Called from the key listener on the event thread. */
    public static void handleBackspace() {
        if (!awaitingInput || inputLine == null || inputCursor == 0) {
            return;
        }
        currentInput = currentInput.substring(0, inputCursor - 1)
            + currentInput.substring(inputCursor);
        inputCursor--;
        AudioManager.playTypingClick();
        refreshInputLine();
    }

    /**
     * Called from the key listener on the event thread. An empty answer is
     * refused: the caret stays put and the prompt keeps waiting, so nothing
     * downstream ever sees, or complains about, a blank answer.
     */
    public static void handleEnter() {
        if (!awaitingInput) {
            // Nothing is being answered, so Enter means "hurry up".
            skipRequested = true;
            skipTyping();
            return;
        }
        if (currentInput.trim().isEmpty()) {
            return;
        }
        awaitingInput = false;
        stopCaretBlink();

        String submitted = currentInput;
        if (inputLine != null) {
            inputLine.setText(promptSymbol + submitted);
        }
        inputLine = null;
        currentInput = "";
        inputCursor = 0;

        if (inputHistory.isEmpty()
            || !inputHistory.get(inputHistory.size() - 1).equals(submitted)) {
            inputHistory.add(submitted);
            if (inputHistory.size() > MAX_HISTORY) {
                inputHistory.remove(0);
            }
        }
        historyIndex = inputHistory.size();

        AudioManager.playTypingClick();

        if (inputResult != null) {
            inputResult.offer(submitted);
            inputResult = null;
        }
    }

    /** Deletes the character under the caret. */
    public static void handleDelete() {
        if (!awaitingInput || inputLine == null
            || inputCursor >= currentInput.length()) {
            return;
        }
        currentInput = currentInput.substring(0, inputCursor)
            + currentInput.substring(inputCursor + 1);
        AudioManager.playTypingClick();
        refreshInputLine();
    }

    /** Moves the caret by delta characters, clamped to the answer. */
    public static void moveCursor(int delta) {
        if (!awaitingInput || inputLine == null) {
            return;
        }
        int next = Math.max(0, Math.min(currentInput.length(), inputCursor + delta));
        if (next == inputCursor) {
            return;
        }
        inputCursor = next;
        refreshInputLine();
    }

    public static void moveCursorToStart() {
        if (!awaitingInput || inputLine == null) {
            return;
        }
        inputCursor = 0;
        refreshInputLine();
    }

    public static void moveCursorToEnd() {
        if (!awaitingInput || inputLine == null) {
            return;
        }
        inputCursor = currentInput.length();
        refreshInputLine();
    }

    /** direction -1 recalls older answers, +1 moves back towards the draft. */
    public static void handleHistory(int direction) {
        if (!awaitingInput || inputLine == null || inputHistory.isEmpty()) {
            return;
        }
        int size = inputHistory.size();
        if (direction < 0) {
            if (historyIndex == size) {
                inputDraft = currentInput;
            }
            if (historyIndex > 0) {
                historyIndex--;
            }
        } else if (historyIndex < size) {
            historyIndex++;
        }
        currentInput = historyIndex >= size
            ? inputDraft
            : inputHistory.get(historyIndex);
        inputCursor = currentInput.length();
        refreshInputLine();
    }

    /** Redraws the prompt with the caret in the right place. */
    private static void refreshInputLine() {
        if (inputLine == null) {
            return;
        }
        int cursor = Math.max(0, Math.min(inputCursor, currentInput.length()));
        inputLine.setText(promptSymbol
            + currentInput.substring(0, cursor)
            + (caretVisible ? CARET : " ")
            + currentInput.substring(cursor));
        canvas.repaint();
    }

    public static boolean isAwaitingInput() {
        return awaitingInput;
    }

    // --------------------------------------------------------------- scroll

    /**
     * Scrolls the log, clamped so the newest line can never be pushed above its
     * resting position and the oldest can never be dragged below the top edge.
     * Without this the player could scroll the conversation into empty space and
     * lose it, which was a known rough edge in the original.
     *
     * @param delta positive scrolls back towards older text
     * @return true if anything actually moved, so the caller knows to click
     */
    public static boolean scroll(int delta) {
        if (canvas == null || activeLines.isEmpty()) {
            return false;
        }

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (TextData line : activeLines) {
            minY = Math.min(minY, line.y);
            maxY = Math.max(maxY, line.y);
        }

        int height = canvas.getViewHeight() > 0 ? canvas.getViewHeight() : 800;
        int restingY = height - scaled(BASE_HOME_MARGIN);

        // Never scroll the newest line above where it normally sits.
        int minDelta = restingY - maxY;
        // Never drag the oldest line below the top margin.
        int maxDelta = scaled(BASE_TOP_MARGIN) - minY;

        if (maxDelta < minDelta) {
            // Log is shorter than one screen: pin it in place.
            maxDelta = minDelta;
        }

        int clamped = Math.max(minDelta, Math.min(maxDelta, delta));
        if (clamped == 0) {
            return false;
        }

        scrollOffset += clamped;
        canvas.scrollAllText(clamped);
        // Scrolling can carry a run into or out of the picture.
        syncGlitches();
        return true;
    }

    /** True when the log has been scrolled back off its newest line. */
    public static boolean isScrolledBack() {
        return scrollOffset != 0;
    }

    /** How far one PageUp/PageDown press moves the log. */
    public static int pageStep() {
        int height = canvas != null && canvas.getViewHeight() > 0
            ? canvas.getViewHeight()
            : 800;
        return Math.max(1, (int) (height * 0.8));
    }

    /** Wipes the screen, for returning to the boot menu. */
    public static void clear() {
        SwingUtilities.invokeLater(() -> {
            stopGlitches();
            messageQueue.clear();
            activeLines.clear();
            canvas.pruneText(0);
            scrollOffset = 0;
            canvas.repaint();
        });
    }
}
