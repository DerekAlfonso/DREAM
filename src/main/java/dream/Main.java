package dream;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;

/**
 * D.R.E.A.M - Digital Recreation of Emotional Aptitude Model.
 *
 * Entry point. Builds the window on the Swing event thread, then hands the
 * story to a worker thread so its pauses never freeze rendering or input. The
 * original did all of this on one thread, which is why the window stopped
 * responding while audio played.
 * 
 * This is the third version of this game, the first version was a throw-away project
 * I made for my CS class at the time that I ended up really enjoying, so I remade it with
 * the graphics library made by the learning platform I was on (CodeHS), and now I've remade 
 * it without those dependencies
 *
 * Run with {@code --debug} for verbose logging.
 */
public final class Main {

    /**
     * Smallest content area the terminal is laid out for. Below this the text
     * column wraps constantly and only a handful of lines fit on screen.
     */
    private static final int MIN_CONTENT_WIDTH = 800;
    private static final int MIN_CONTENT_HEIGHT = 600;

    private static JFrame frame;
    private static Game game;

    /** Set by --doom: jump straight to the easter egg instead of the menu. */
    static boolean startInDoom = false;

    private Main() { }

    public static void main(String[] args) {
        for (String arg : args) {
            if ("--debug".equalsIgnoreCase(arg)) {
                Log.debugMode = true;
            } else if ("--doom".equalsIgnoreCase(arg)) {
                // Skips the boot menu and drops straight into doom.exe. Saves
                // sitting through the loading bar every time you want to poke
                // at the renderer.
                startInDoom = true;
            }
        }

        // Crisper text on high-DPI Windows displays.
        System.setProperty("sun.java2d.uiScale.enabled", "false");

        Settings.load();
        SaveState.load();

        // Decode the short effects up front so the first keystroke is instant.
        AudioManager.preloadAsync(AudioManager.KEYBOARD_CLICKS);
        AudioManager.preloadAsync(AudioManager.SCROLL_SOUNDS);

        try {
            SwingUtilities.invokeAndWait(Main::buildWindow);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (InvocationTargetException e) {
            Log.error("Could not open the window.", e.getCause());
            return;
        }

        // The boot whirr is fired by the story once the loading bar finishes,
        // so the machine sounds like it is spinning up as it comes to life.
        game = new Game();
        Thread storyThread = new Thread(game, "dream-story");
        storyThread.setDaemon(true);
        storyThread.start();
    }

    private static void buildWindow() {
        Font terminalFont = Assets.loadTerminalFont();

        CurveCanvas canvas = new CurveCanvas();
        InputController input = new InputController();
        canvas.addKeyListener(input);
        canvas.addMouseWheelListener(input);

        Terminal.attach(canvas, terminalFont);

        // The picture is 4:3, so the window opens 4:3 rather than matching the
        // display. Maximising afterwards leaves black bezel either side, which
        // is the point: the game never stretches.
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int windowHeight = (int) (screen.height * 0.85);
        canvas.setPreferredSize(new Dimension(
            windowHeight / 3 * 4, windowHeight));

        frame = new JFrame("D.R.E.A.M");
        frame.setIconImages(Assets.loadWindowIcons());
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.add(canvas);
        frame.pack();

        // The frame minimum includes the title bar and borders, so add them
        // once the window exists. Clamp to the screen for small displays.
        Insets insets = frame.getInsets();
        frame.setMinimumSize(new Dimension(
            Math.min(screen.width, MIN_CONTENT_WIDTH + insets.left + insets.right),
            Math.min(screen.height, MIN_CONTENT_HEIGHT + insets.top + insets.bottom)));

        frame.setLocationRelativeTo(null);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });

        frame.setVisible(true);

        // Scanlines need the real canvas size, which only exists once shown.
        canvas.rebuildScanlines();
        canvas.requestFocusInWindow();
    }

    /** Saves, releases the sound card and closes down cleanly. */
    public static void shutdown() {
        Log.info("Shutting down.");
        if (game != null) {
            game.stop();
        }
        Settings.save();
        SaveState.save();
        AudioManager.dispose();

        if (frame != null) {
            frame.dispose();
        }
        System.exit(0);
    }
}
