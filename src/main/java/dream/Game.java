package dream;

import java.awt.Color;
import java.util.List;
import java.util.Random;

/**
 * The story itself, run on its own thread so it can sleep between beats without
 * freezing the window.
 *
 * <p>This class owns the loop, input and the boot menu. The story, minigames
 * and the settings/credits screens live in their own classes. The dialogue is
 * unchanged from the original. What changed is the plumbing around it: string
 * comparisons that used {@code ==} never matched, so name validation and the
 * credits screen's BACK command could not fire, and the boot menu never redrew
 * itself after an option finished.
 */
public class Game implements Runnable {

    private final Random random = new Random();
    private volatile boolean running = true;
    static int standardSize = 15; // denotes the standard size for text, all text should use this as a reference

    /** The size the rest of the game uses for body text. */
    static int getStandardSize() {
        return standardSize;
    }

    private final Minigames minigames = new Minigames(this);
    private final Story story = new Story(this, minigames);
    private final Screens screens = new Screens(this);

    /** Thrown when the player types MENU to leave the story for the boot menu. */
    static final class ReturnToMenu extends RuntimeException {
        ReturnToMenu() {
            super(null, null, false, false);
        }
    }

    @Override
    public void run() {
        try {
            if (Main.startInDoom) {
                Doom.play(this::ask);
            }

            if (Main.startStory) {
                prepareDevStart();
                try {
                    story.play();
                } catch (ReturnToMenu ignored) {
                    // The MENU command already saved and asked to come back here.
                }
            } else {
                boot();
            }

            while (running) {
                try {
                    showMenu();
                    handleChoice(ask());
                } catch (ReturnToMenu e) {
                    // ask() already saved; just show the menu again.
                    Terminal.blankLine();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.info("Story thread interrupted; shutting down.");
        } catch (Exception e) {
            Log.error("The story thread hit an unexpected problem.", e);
        }
    }

    public void stop() {
        running = false;
    }

    boolean isRunning() {
        return running;
    }

    // ----------------------------------------------------------------- input

    /**
     * Reads every normal prompt, so "quit", "exit" and "menu" can be handled
     * from anywhere. If the player cancels the quit confirmation, this asks
     * again instead of passing the quit command to the current screen.
     */
    String ask() throws InterruptedException {
        while (true) {
            String input = Terminal.prompt().trim();

            if (!input.equalsIgnoreCase("quit")
                && !input.equalsIgnoreCase("exit")) {
                if (input.equalsIgnoreCase("menu")) {
                    SaveState.save();
                    Terminal.print("progress saved. returning to the menu.",
                        20, standardSize, Color.CYAN);
                    throw new ReturnToMenu();
                }
                return input;
            }
            quit();
        }
    }

    /**
     * Confirms the quit, then says goodbye and shuts down. Returns to the
     * caller when the player decides to keep playing.
     */
    private void quit() throws InterruptedException {
        Terminal.print("are you sure you want to quit? type YES to confirm, or NO to stay.",
            20, standardSize, Color.WHITE);
        String answer = Terminal.prompt().trim();

        if (!answer.equalsIgnoreCase("yes") && !answer.equalsIgnoreCase("y")) {
            Terminal.print("okay, staying right here :)", 20, standardSize, Color.GREEN);
            return;
        }

        running = false;
        Terminal.print("Goodbye.", 40, standardSize, Color.GREEN);
        Terminal.awaitIdle();
        pause(800);
        Main.shutdown();
    }

    /**
     * Words that back out of a sub-screen to the boot menu. "exit" is
     * deliberately not one of them: it quits the program instead.
     */
    static boolean isBackCommand(String input) {
        return input.equalsIgnoreCase("back")
            || input.equalsIgnoreCase("return")
            || input.equalsIgnoreCase("home");
    }

    // ------------------------------------------------------------------ boot

    private void boot() throws InterruptedException {
        List<String> splashes = Assets.loadSplashes();
        String chosenSplash = splashes.get(random.nextInt(splashes.size()));

        Terminal.print("INITIALIZING SYSTEM...", 20, standardSize, Color.GREEN);
        if (Settings.fastBoot) {
            AudioManager.playMusic(AudioManager.STARTUP_WHIRR, false);
            Terminal.print("Loading complete, enjoy!", 20, standardSize, Color.GREEN);
        } else {
            pause(1200);
            runLoadingBar();
            pause(600);
        }
        Terminal.print("- " + chosenSplash, 20, standardSize, Color.WHITE);
        Terminal.print("----------------------", 0, standardSize, Color.WHITE);
        if (!Settings.fastBoot) {
            pause(2000);
        }
    }

    /** How many cells wide the bar's track is. */
    private static final int BAR_WIDTH = 24;

    private static final char BAR_FILLED = '#';
    private static final char BAR_EMPTY = '-';

    /**
     * A fake load, because booting instantly does not feel like putting a disc
     * in. Each percentage point waits a random 0-100ms, so the bar stutters and
     * surges the way a real one does instead of ticking along evenly.
     */
    private void runLoadingBar() throws InterruptedException {
        Terminal.StatusLine bar = Terminal.openStatusLine(standardSize, Color.GREEN);

        for (int percent = 0; percent <= 100; percent++) {
            bar.set(renderBar(percent));
            pause(random.nextInt(101));
        }

        // The machine spins up the moment the load finishes, not at launch.
        AudioManager.playMusic(AudioManager.STARTUP_WHIRR, false);

        Terminal.print("Loading complete, enjoy!", 20, standardSize, Color.GREEN);
    }

    /**
     * Builds one frame of the bar. The percentage is padded to three columns so
     * the bar never shifts sideways as the number grows.
     */
    private static String renderBar(int percent) {
        // Integer division floors, so the track only reads as full at exactly
        // 100%. Rounding would fill it a percentage point early.
        int filled = percent * BAR_WIDTH / 100;

        StringBuilder track = new StringBuilder(BAR_WIDTH);
        for (int cell = 0; cell < BAR_WIDTH; cell++) {
            track.append(cell < filled ? BAR_FILLED : BAR_EMPTY);
        }

        return String.format("Loading: %3d%%  [%s]", percent, track);
    }

    private void showMenu() {
        Terminal.print("Select a file to open: ", 20, standardSize, Color.WHITE);
        Terminal.print("[-] ProjectDream.iso", 20, standardSize, Color.WHITE);
        Terminal.print("[-] settings.bin", 20, standardSize, Color.WHITE);
        Terminal.print("[-] credits.txt", 20, standardSize, Color.WHITE);
    }

    private void handleChoice(String choice) throws InterruptedException {
        String normalized = choice.toLowerCase();

        if (normalized.equals("projectdream.iso")) {
            story.play();
        } else if (normalized.equals("settings.bin")) {
            screens.settings();
        } else if (normalized.equals("credits.txt")) {
            screens.credits();
        } else if (normalized.equals("doom.exe")) {
            // But can it run doom? -- not present in list
            Doom.play(this::ask);
        } else {
            Terminal.print("please select a valid file name", 20, 15, Color.RED);
        }
    }

    /** Seeds or jumps to a save for the --new / --skip-intro / --scene flags. */
    private void prepareDevStart() {
        if (Main.startNew) {
            SaveState.reset();
        }
        if (Main.startScene != null || Main.skipIntro) {
            SaveState.stage = SaveState.STAGE_CYA;
            SaveState.cyaLocation = Main.startScene;
            if (SaveState.username == null) {
                SaveState.username = "Dev";
            }
            if (SaveState.cpuName == null) {
                SaveState.cpuName = "DREAM";
            }
            if (SaveState.favoriteColor == null) {
                SaveState.favoriteColor = "green";
            }
            SaveState.save();
        }
    }

    // ------------------------------------------------------------------ waits

    /** Waits, but returns early if the player presses Enter. */
    void pause(long millis) throws InterruptedException {
        Terminal.pause(millis);
    }

    /** Waits only when fast boot is off, for scene transitions. */
    void loadingPause(long millis) throws InterruptedException {
        if (!Settings.fastBoot) {
            Terminal.pause(millis);
        }
    }
}
