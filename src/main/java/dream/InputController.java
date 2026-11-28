package dream;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

/**
 * Turns raw keyboard and wheel events into terminal actions.
 *
 * <p>Split out of the main class so the story code no longer has to implement
 * three listener interfaces it mostly left empty.
 */
public class InputController extends KeyAdapter implements MouseWheelListener {

    private static final int SCROLL_SPEED = 25;

    /** True while Enter is held, so auto-repeat cannot skip or submit twice. */
    private boolean enterHeld = false;

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_BACK_SPACE:
                if (e.isControlDown()) {
                    Terminal.deleteWordBackward();
                } else {
                    Terminal.handleBackspace();
                }
                break;
            case KeyEvent.VK_DELETE:
                if (e.isControlDown()) {
                    Terminal.deleteWordForward();
                } else {
                    Terminal.handleDelete();
                }
                break;
            case KeyEvent.VK_LEFT:
                Terminal.moveCursor(-1);
                break;
            case KeyEvent.VK_RIGHT:
                Terminal.moveCursor(1);
                break;
            case KeyEvent.VK_HOME:
                if (Terminal.isAwaitingInput()) {
                    Terminal.moveCursorToStart();
                } else {
                    scrollBy(Integer.MAX_VALUE);
                }
                break;
            case KeyEvent.VK_END:
                // End gets the log back to the newest text. It only moves the
                // input caret when the log is already there and a prompt is up.
                if (Terminal.isAwaitingInput() && !Terminal.isScrolledBack()) {
                    Terminal.moveCursorToEnd();
                } else {
                    scrollBy(Integer.MIN_VALUE);
                }
                break;
            case KeyEvent.VK_UP:
                Terminal.handleHistory(-1);
                break;
            case KeyEvent.VK_DOWN:
                Terminal.handleHistory(1);
                break;
            case KeyEvent.VK_PAGE_UP:
                // Page keys scroll even while an answer is being typed, so
                // earlier text stays reachable without the mouse.
                scrollBy(Terminal.pageStep());
                break;
            case KeyEvent.VK_PAGE_DOWN:
                scrollBy(-Terminal.pageStep());
                break;
            case KeyEvent.VK_ENTER:
                if (!enterHeld) {
                    enterHeld = true;
                    Terminal.handleEnter();
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            enterHeld = false;
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
        char c = e.getKeyChar();
        // Backspace and Enter arrive here too; they are handled in keyPressed.
        if (c >= 32 && c <= 126) {
            Terminal.handleCharTyped(c);
        }
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        scrollBy(-e.getWheelRotation() * SCROLL_SPEED);
    }

    /**
     * Scrolls the log and clicks only when the view actually moved, so hitting
     * the end is silent rather than rattling.
     */
    private static void scrollBy(int amount) {
        if (Terminal.scroll(amount)) {
            AudioManager.playRandomSfx(AudioManager.SCROLL_SOUNDS);
        }
    }
}
