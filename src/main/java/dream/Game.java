package dream;

import java.awt.Color;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * 
 * The story itself, run on its own thread so it can sleep between beats without
 * freezing the window.
 *
 * <p>The dialogue is unchanged from the original. What changed is the plumbing
 * around it: string comparisons that used {@code ==} never matched, so name
 * validation and the credits screen's BACK command could not fire, and the boot
 * menu never redrew itself after an option finished.
 */
public class Game implements Runnable {

    private static final Color PURPLE = new Color(141, 20, 222);

    /** The three moves, in the order the game lists them. */
    private static final String[] MOVES = {"rock", "paper", "scissors"};

    private final Random random = new Random();
    private volatile boolean running = true;
    static int standardSize = 15; // denotes the standard size for text, all text should use this as a reference

    /** The size the rest of the game uses for body text. */
    static int getStandardSize() {
        return standardSize;
    }

    /** The current UTC time, formatted for the report log header. */
    private static String utcTimestamp() {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now());
    }

    @Override
    public void run() {
        try {
            if (Main.startInDoom) {
                Doom.play(this::ask);
            } else {
                boot();
            }
            if (Main.startStory) {
                runShortcut();
            }
            while (running) {
                showMenu();
                handleChoice(ask());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.info("Story thread interrupted; shutting down.");
        } catch (Exception e) {
            Log.error("The story thread hit an unexpected problem.", e);
        }
    }

    /**
     * Starts the story straight away for a development flag instead of waiting
     * for the boot menu. A --scene checkpoint marks the CYA as in progress and
     * saves it, so the game can be quit and resumed from the same place.
     */
    private void runShortcut() throws InterruptedException {
        if (Main.startScene != null) {
            SaveState.stage = SaveState.STAGE_CYA;
            SaveState.cyaLocation = Main.startScene;
            SaveState.save();
            playCYA();
            return;
        }
        if (Main.skipIntro) {
            // The CYA prints the player's name, and a fresh save has none.
            if (!SaveState.hasProfile()) {
                SaveState.username = "tester";
                SaveState.cpuName = "ERIS";
                SaveState.favoriteColor = "green";
            }
            SaveState.stage = SaveState.STAGE_CYA;
            SaveState.save();
        }
        runProjectDream();
    }

    public void stop() {
        running = false;
    }

    // ----------------------------------------------------------------- input

    /**
     * Reads every normal prompt, so "quit" and "exit" can be handled from
     * anywhere. If the player cancels the quit confirmation, this asks again
     * instead of passing the quit command to the current screen.
     */
    public String ask() throws InterruptedException {
        while (true) {
            String input = Terminal.prompt().trim();

            if (!input.equalsIgnoreCase("quit")
                && !input.equalsIgnoreCase("exit")) {
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
    public static boolean isBackCommand(String input) {
        return input.equalsIgnoreCase("back")
            || input.equalsIgnoreCase("return")
            || input.equalsIgnoreCase("home");
    }

    // ------------------------------------------------------------------ boot

    private void boot() throws InterruptedException {
        List<String> splashes = Assets.loadSplashes();
        String chosenSplash = splashes.get(random.nextInt(splashes.size()));

        Terminal.print("INITIALIZING SYSTEM...", 20, standardSize, Color.GREEN);
        loadingPause(1200);
        runLoadingBar();
        loadingPause(600);
        Terminal.print("- " + chosenSplash, 20, standardSize, Color.WHITE);
        Terminal.divider();
        loadingPause(2000);
    }

    /** How many cells wide the bar's track is. */
    private static final int BAR_WIDTH = 24;

    private static final char BAR_FILLED = '#';
    private static final char BAR_EMPTY = '-';

    /**
     * A fake load, because booting instantly does not feel like putting a disc
     * in. Each percentage point waits a random 0-100ms, so the bar stutters and
     * surges the way a real one does instead of ticking along evenly. With
     * fastBoot the bar is skipped; the machine still spins up and says so.
     */
    private void runLoadingBar() throws InterruptedException {
        if (!Settings.fastBoot) {
            Terminal.StatusLine bar = Terminal.openStatusLine(standardSize, Color.GREEN);

            for (int percent = 0; percent <= 100; percent++) {
                bar.set(renderBar(percent));
                pause(random.nextInt(101));
            }
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
            runProjectDream();
        } else if (normalized.equals("settings.bin")) {
            runSettings();
        } else if (normalized.equals("credits.txt")) {
            runCredits();
        } else if (normalized.equals("cheats.img")) {
            runCheats();
        } else if (normalized.equals("doom.exe")) {
            // But can it run doom? -- not present in list
            Doom.play(this::ask);
        } else {
            Terminal.print("please select a valid file name", 20, 15, Color.RED);
        }
    }

    /**
     * The unlisted CHEATS.IMG file: CYA checkpoints for testing a scene
     * without playing up to it. It never appears in the boot menu.
     */
    private void runCheats() throws InterruptedException {
        Terminal.divider();
        Terminal.print("CHEATS.IMG", 20, standardSize + 2, Color.GREEN);
        Terminal.print("test checkpoints. this file is not listed.", 15, standardSize, Color.WHITE);
        printCheckpoint("1", "the cemetary, start of the CYA");
        printCheckpoint("2", "the outer shack");
        printCheckpoint("3", "the inner shack");
        printCheckpoint("4", "the cellar (hatch open, lantern and rusty key carried)");
        printCheckpoint("5", "the house (cellar puzzle solved)");
        printCheckpoint("6", "upstairs (cellar puzzle solved)");
        printCheckpoint("7", "room 101 (peace purpose)");
        printCheckpoint("8", "room 102 (answers purpose)");
        printCheckpoint("9", "room 103 (vengeance purpose)");
        printCheckpoint("0", "back");

        String choice = ask().trim();
        if (choice.isEmpty() || choice.equals("0") || isBackCommand(choice)) {
            return;
        }
        CyaLocation destination = switch (choice) {
            case "1" -> CyaLocation.CEMETARY;
            case "2" -> CyaLocation.SHACK_OUTER;
            case "3" -> CyaLocation.SHACK_INNER;
            case "4" -> CyaLocation.CELLAR;
            case "5" -> CyaLocation.HOUSE;
            case "6" -> CyaLocation.UPSTAIRS;
            case "7" -> CyaLocation.ROOM_101;
            case "8" -> CyaLocation.ROOM_102;
            case "9" -> CyaLocation.ROOM_103;
            default -> null;
        };
        if (destination == null) {
            Terminal.print("no checkpoint called \"" + choice + "\".", 15, standardSize, Color.RED);
            return;
        }
        loadCheckpoint(destination);
    }

    private static void printCheckpoint(String key, String label) {
        Terminal.print("[" + key + "] " + label, 15, standardSize, Color.WHITE);
    }

    /**
     * Drops the CYA at one checkpoint with the flags that point assumes, then
     * runs the main story from there, so the CYA ends with its report log.
     * The room checkpoints also set the purpose, so the ending door and its
     * key behave the way they would in a real run.
     */
    private void loadCheckpoint(CyaLocation destination) throws InterruptedException {
        if (!SaveState.hasProfile()) {
            SaveState.username = "tester";
            SaveState.cpuName = "ERIS";
        }
        if (SaveState.favoriteColor == null) {
            SaveState.favoriteColor = "green";
        }

        if (destination == CyaLocation.ROOM_101) {
            SaveState.purpose = "peace";
        } else if (destination == CyaLocation.ROOM_102) {
            SaveState.purpose = "answers";
        } else if (destination == CyaLocation.ROOM_103) {
            SaveState.purpose = "vengeance";
        }

        if (destination == CyaLocation.CELLAR) {
            SaveState.hatchBroken = true;
            SaveState.valvesOiled = false;
            SaveState.panelOpen = false;
            SaveState.cellarPuzzleSolved = false;
            Cellar.resetProgress();
            SaveState.addItem("rustyLantern");
            SaveState.addItem("rustyKey");
        }

        if (destination == CyaLocation.HOUSE || destination == CyaLocation.UPSTAIRS
                || destination == CyaLocation.ROOM_101 || destination == CyaLocation.ROOM_102
                || destination == CyaLocation.ROOM_103) {
            SaveState.hatchBroken = true;
            SaveState.valvesOiled = true;
            SaveState.panelOpen = true;
            SaveState.cellarPuzzleSolved = true;
        }

        SaveState.stage = SaveState.STAGE_CYA;
        SaveState.cyaLocation = destination;
        SaveState.save();

        Terminal.print("loading checkpoint: " + destination.displayName, 20, standardSize, Color.GREEN);
        runProjectDream();
    }

    // --------------------------------------------------------- ProjectDream.iso the main game file

    private void runProjectDream() throws InterruptedException {
        Terminal.divider();

        if (SaveState.stage == null && !SaveState.hasProfile()) {
            Terminal.print("Hello friend :)", 20, standardSize, Color.WHITE);
            Terminal.print("Thank you for choosing to help participate in the testing for the. . .",
                20, standardSize, Color.WHITE);
            Terminal.print("Digital Recreation of Emotional Aptitude Model",
                20, standardSize + 2, Color.CYAN);
            Terminal.print("the Eris Digital company appreciates your help",
                20, standardSize, Color.WHITE);
            Terminal.print("My job is to keep you company and entertained!",
                20, standardSize, Color.WHITE);
            Terminal.print("Think of me as your personal digital friend :D",
                20, standardSize, Color.WHITE);
            Terminal.print("lets start off with the simple stuff, whats your name?",
                20, standardSize, Color.WHITE);

            SaveState.username = askForName(2);
            Terminal.print(SaveState.username + ", huh?", 20, standardSize, Color.WHITE);
            Terminal.print("I like that name!", 20, standardSize, Color.WHITE);
            Terminal.print("Now I need a name. . . ", 20, standardSize, Color.WHITE);
            Terminal.print("what should I be called?", 20, standardSize, Color.WHITE);

            SaveState.cpuName = askForName(1);
            Terminal.print(SaveState.cpuName + "?", 200, standardSize, Color.WHITE);
            Terminal.print(". . .", 500, standardSize, Color.WHITE);
            Terminal.print("thats nice.", 200, standardSize, Color.WHITE);
            Terminal.print("Okay one last question before the fun part..", 20, standardSize, Color.WHITE);
            Terminal.print("what's your favorite color?", 20, standardSize, Color.WHITE);

            SaveState.favoriteColor = askForColor();
            Terminal.print(SaveState.favoriteColor + "? Good choice :3", 20, standardSize, NamedColors.display(SaveState.favoriteColor));
            Terminal.print("Okay now on to the fun part!", 20, standardSize, Color.WHITE);
            Terminal.print("I want to get to know you better", 20, standardSize, Color.WHITE);
            Terminal.print("but I can't just ask you questions all day (even if I want to)", 20, standardSize, Color.WHITE);
            Terminal.print("so lets play a few games together :)", 20, standardSize, Color.WHITE);

            SaveState.stage = SaveState.STAGE_RPS;
            SaveState.save();
        } else {
            if (SaveState.stage == null) {
                // Save from before checkpoints existed: use what it already knows.
                SaveState.stage = SaveState.cyaLocation != null
                    ? SaveState.STAGE_CYA
                    : SaveState.STAGE_RPS;
                SaveState.save();
            }
            Terminal.print("welcome back, " + SaveState.username + " :3", 20, standardSize, Color.GREEN);
            Terminal.print("lets continue where we left off, shall we?", 20, standardSize, Color.WHITE);
        }

        if (SaveState.STAGE_RPS.equals(SaveState.stage)) {
            playRockPaperScissors();
            Terminal.print("bored already?", 20, standardSize, Color.WHITE);
            Terminal.print("alrighty we can do something else", 20, standardSize, Color.WHITE);
            Terminal.print("let me think for a second", 20, standardSize, Color.WHITE);
            pause(1500);
            Terminal.print(" . . . . .", 200, standardSize, Color.WHITE);
            pause(1200);
            Terminal.print("I got it!", 20, standardSize, Color.WHITE);
            Terminal.print("lets play a game of HangMan!", 20, standardSize, Color.WHITE);
            Terminal.print("I'll chose a random word and you have to guess what it is!", 20, standardSize, Color.WHITE);
            playHangman();

            Terminal.print("alright, alright, you're right, we can move onto something more interesting", 20, standardSize, Color.WHITE);
            Terminal.print("how about something with a little more complexity..", 20, standardSize, Color.WHITE);
            Terminal.print("how about something like.. A DUNGEON CRAWLER!", 20, standardSize, Color.WHITE);
            pause(500);
            Terminal.print("yeah, that'll be fun :)", 20, standardSize, Color.WHITE);
            boolean dungeonPlayed = playDungeonCrawler();

            SaveState.stage = SaveState.STAGE_CYA;
            SaveState.save();

            if (dungeonPlayed) {
                Terminal.print("okay, okay, fine.", 20, standardSize, Color.WHITE);
                Terminal.print("I made a little something while you were fighting through the dungeon", 20, standardSize, Color.WHITE);
                Terminal.print("I think I've really out done myself here..", 20, standardSize, Color.WHITE);
                Terminal.print("drumroll please", 20, standardSize, Color.WHITE);
                //AudioManager.playRandomSfx(AudioManager.SCROLL_SOUNDS);
                Terminal.print(". . .", 600, standardSize, Color.WHITE);
                Terminal.print("I can't find a drumroll in my files, just imagine what it would sound like", 20, standardSize, Color.WHITE);
                Terminal.print("doesn't matter, because its time to play an adventure..", 20, standardSize, Color.WHITE);
                Terminal.print("I warn you, it's a long story but I promise it'll be good :)", 20, standardSize, Color.WHITE);
            } else {
                playSkippedDungeonDialogue();
            }
        }

        playCYA();


        /*
        *  ending scene
        */
        Terminal.print("CONNECTION TERMINATED - ", 15, Game.standardSize, Color.WHITE);
        Terminal.pause(2000);
        Terminal.clear();
        Terminal.divider();
        Terminal.print("ERIS Digital DREAM project Report Log:", 15, Game.standardSize, Color.WHITE);
        Terminal.print(utcTimestamp(), 15, Game.standardSize, Color.WHITE);
        Terminal.blankLine();
        Terminal.print("System info dump - ", 15, Game.standardSize, Color.WHITE);
        Terminal.print("SystemName: " + SaveState.cpuName, 15, Game.standardSize, Color.WHITE);
        Terminal.print("SystemVersion: 2.1956.85", 15, Game.standardSize, Color.WHITE);
        Terminal.print("UserName: " + SaveState.username, 15, Game.standardSize, Color.WHITE);
        Terminal.print("UserFavColor: " + SaveState.favoriteColor, 15, Game.standardSize, Color.WHITE);
        Terminal.blankLine();
        Terminal.print("Log Cont.", 15, Game.standardSize, Color.WHITE);
        Terminal.print("Archival Restoration Notes:", 15, Game.standardSize, Color.WHITE);
        if ("peace".equals(SaveState.purpose))
        {
        Terminal.print("I don't know why higher ups care about this project at all, it seems unstable and ineffective. " + 
            "It got really weird with me towards the end, I'm sure Ellen will have a field day telling me how cool this " + 
            "project is and inundating me with facts for a project that's probably older than both of us combined. Whatever, " +
            "I can't seem to find a date for when this was made, but it seems pretty old. Ask Ellen if she knows anything, she usually does.", 15, Game.standardSize, Color.WHITE);
        Terminal.print("-Ted Soul", 15, Game.standardSize, NamedColors.display(SaveState.favoriteColor));
        } else if ("answers".equals(SaveState.purpose))
        {
        Terminal.print("Yeah I'm gonna be honest I don't really know how to describe what this one did. It got all philisophical with me and then " + 
            "what I can only imagine is the computer version of suicide. Anyway, if the higher ups want this project back they're gonna have to remake " +
            "it cause I just watched it purposefully set off a guard rail to delete itself. I really wish this company would start making normal software. " +
            "Anyway, hope you're having a good day Ellen (whenever you get to reading this) cause my day just got really weird.", 15, Game.standardSize, Color.WHITE);
        Terminal.print("-Ted Soul", 15, Game.standardSize, NamedColors.display(SaveState.favoriteColor));
        } else if ("vengeance".equals(SaveState.purpose))
        {
            Terminal.print("I'm a little worried, this was the project assigned to Ted today, logs showed he went through it but left before writing his report. " +
                "I wasn't able to recover much from his files, most we're scrubbed or just gone? I guess? I can't believe I'm saying this, but I'm really worried about Ted, " +
                "he said he was going for a 'smoke break', and that stubborn jackass suddenly isn't there to tell me how boring this job is. I hope he's okay. Wherever he might be. " +
                "If anyone else is reading this, please remind me to rearchive this project properly, I don't want higherups to kill me.", 15, Game.standardSize, Color.WHITE);
        Terminal.print("-Ellen Leonida", 15, Game.standardSize, NamedColors.randomDisplayExcluding(SaveState.favoriteColor));
        }

        Terminal.awaitIdle();

        // The story is over, so clear the save and start the next run clean.
        Terminal.print("that's the end. Restarting Archival System Managment",
            20, standardSize, Color.GREEN);
        pause(2500);
        Terminal.clear();
    }

    /**
     * Plays rounds of rock, paper, scissors until the player types CANCEL.
     * There is no fixed round count: the player decides when they have had
     * enough.
     */
    private void playRockPaperScissors() throws InterruptedException {
        Terminal.blankLine();
        pause(1500);
        Terminal.divider();
        Terminal.print("Rock, Paper, Scissors!", 20, 15, Color.CYAN);
        Terminal.print("type rock, paper or scissors.",
            15, 15, Color.WHITE);
        Terminal.print("type CANCEL to stop playing.", 15, 15, Color.WHITE);

        while (running) {
            Terminal.print("what's your move?", 15, 15, Color.WHITE);

            String input = ask();
            if (isCancel(input)) {
                break;
            }

            String playerMove = toMove(input);
            if (playerMove == null) {
                Terminal.print("please type rock, paper, scissors or CANCEL.",
                    15, 15, Color.RED);
                continue;
            }

            playRound(playerMove);
        }

        Terminal.print("good game :)", 20, 15, Color.GREEN);
    }

    /** Turns player input into one of the three moves, or null if it is not one. */
    private static String toMove(String input) {
        String normalized = input.toLowerCase();
        if (normalized.isEmpty()) {
            return null;
        }
        // Any shortening works: r, ro, roc, rock; p, pa, paper; s, sc, scissors.
        for (String move : MOVES) {
            if (move.startsWith(normalized)) {
                return move;
            }
        }
        return null;
    }

    /** The one way out of a minigame: CANCEL, or any of the back words. */
    private static boolean isCancel(String input) {
        return input.equalsIgnoreCase("cancel") || isBackCommand(input);
    }

    /** Picks the computer's move, then says who won the round. */
    private void playRound(String playerMove) {
        String cpuMove = MOVES[random.nextInt(MOVES.length)];

        Terminal.print("you chose " + playerMove + ".", 15, 15, Color.WHITE);
        Terminal.print(SaveState.cpuName + " chose " + cpuMove + ".", 15, 15, Color.WHITE);

        if (playerMove.equals(cpuMove)) {
            Terminal.print("it's a tie!", 15, 15, Color.CYAN);
        } else if (beats(playerMove, cpuMove)) {
            Terminal.print("you win! :D", 20, 15, Color.GREEN);
        } else {
            Terminal.print("I win this one!", 20, 15, Color.RED);
        }
    }

    /** Rock beats scissors, paper beats rock, scissors beat paper. */
    private static boolean beats(String move, String other) {
        return (move.equals("rock") && other.equals("scissors"))
            || (move.equals("paper") && other.equals("rock"))
            || (move.equals("scissors") && other.equals("paper"));
    }

    /**
     * Wrong guesses allowed for a word. The allowance scales with the number of
     * distinct letters, because a long word that repeats itself is far easier
     * than a short one that does not. Four to eight keeps both ends fair.
     */
    private static int hangmanMisses(String word) {
        int distinct = (int) word.chars().distinct().count();
        return Math.max(4, Math.min(8, distinct / 2 + 2));
    }

    /** Used when the random-word service cannot be reached. */
    private static final List<String> HANGMAN_FALLBACK_WORDS = List.of(
        "APPLE", "ANCHOR", "BASKET", "BEACON", "BICYCLE", "BLANKET",
        "BRIDGE", "BUTTER", "CACTUS", "CAMERA", "CANDLE", "CASTLE",
        "CHERRY", "CIRCLE", "CLOUD", "COFFEE", "COMET", "CRICKET",
        "DOLPHIN", "DRAGON", "DREAM", "EAGLE", "ENGINE", "FALCON",
        "FEATHER", "FOREST", "GARDEN", "GUITAR", "HAMMER", "HARBOR",
        "HELMET", "ISLAND", "JACKET", "JUNGLE", "KETTLE", "KITTEN",
        "LADDER", "LANTERN", "LEMON", "MAGNET", "MARBLE", "MEADOW",
        "MONKEY", "MOUNTAIN", "NOTEBOOK", "OCEAN", "ORANGE", "PANDA",
        "PEPPER", "PIANO", "PILLOW", "PLANET", "POCKET", "PUZZLE",
        "RABBIT", "RAINBOW", "ROCKET", "SADDLE", "SCHOOL", "SHADOW",
        "SILVER", "SNOW", "SPIDER", "SPRING", "SUNSET", "TIGER",
        "TUNNEL", "TURTLE", "VALLEY", "WAGON", "WINDOW", "WINTER",
        "YELLOW", "ZEBRA"
    );

    /** Returns one word as a JSON array, for example ["example"]. */
    private static final String WORD_API = "https://random-word-api.herokuapp.com/word";

    /** How many requests to make before using the offline list. */
    private static final int WORD_ATTEMPTS = 2;

    /** Short timeout so a bad connection reaches the retry quickly. */
    private static final Duration WORD_TIMEOUT = Duration.ofSeconds(3);

    /** Brief pause before the single retry. */
    private static final long WORD_RETRY_DELAY_MS = 300L;

    private static final HttpClient WORD_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    /**
     * Plays Hangman until the player cancels. Each win or loss is followed by
     * a choice between a fresh word and leaving, so there is no fixed number
     * of rounds.
     */
    private void playHangman() throws InterruptedException {
        boolean keepPlaying = true;

        while (running && keepPlaying) {
            keepPlaying = playHangmanRound(getHangmanWord());
            if (keepPlaying) {
                keepPlaying = askForHangmanReplay();
            }
        }

    }

    /** Returns false if the player stops during the round. */
    private boolean playHangmanRound(String word) throws InterruptedException {
        Set<Character> guessed = new TreeSet<>();
        Set<Character> missed = new TreeSet<>();
        int allowedMisses = hangmanMisses(word);

        Terminal.print("the word has " + word.length() + " letters.",
            15, 15, Color.WHITE);
        Terminal.print(hangmanMask(word, guessed), 1, 20, Color.CYAN);
        Terminal.print("you get " + allowedMisses + " wrong guesses.",
            15, 15, Color.WHITE);

        while (running) {
            Terminal.print("guess a letter, or type CANCEL to stop playing.",
                15, 15, Color.WHITE);

            String input = ask();
            if (isCancel(input)) {
                return false;
            }

            if (input.length() != 1 || !Character.isLetter(input.charAt(0))) {
                Terminal.print("please guess a single letter.", 15, 15, Color.RED);
                continue;
            }

            char guess = Character.toUpperCase(input.charAt(0));
            if (guessed.contains(guess)) {
                Terminal.print("you already tried " + guess + ".", 15, 15, Color.RED);
                continue;
            }
            guessed.add(guess);

            if (word.indexOf(guess) >= 0) {
                Terminal.print("yes! " + guess + " is in the word.", 15, 15, Color.GREEN);
            } else {
                missed.add(guess);
                Terminal.print("nope. " + guess + " is not in the word.", 15, 15, Color.RED);
            }

            Terminal.print(hangmanMask(word, guessed), 1, 20, Color.CYAN);
            Terminal.print("wrong: " + formatLetters(missed) + "   guesses left: "
                + (allowedMisses - missed.size()), 15, 15,
                missed.size() >= allowedMisses - 1 ? Color.RED : Color.WHITE);

            if (hangmanSolved(word, guessed)) {
                Terminal.print("you got it! the word was " + word + ".",
                    20, 15, Color.GREEN);
                return true;
            }
            if (missed.size() >= allowedMisses) {
                Terminal.print("out of guesses. the word was " + word + ".",
                    20, 15, Color.RED);
                return true;
            }
        }

        return false;
    }

    /** Asks for a new word or a way out, after one round has ended. */
    private boolean askForHangmanReplay() throws InterruptedException {
        Terminal.print("type NEW for a new word, or CANCEL to stop playing.",
            15, 15, Color.WHITE);

        while (running) {
            String input = ask();
            if (input.equalsIgnoreCase("new")) {
                return true;
            }
            if (isCancel(input)) {
                return false;
            }
            Terminal.print("please type NEW or CANCEL.", 15, 15, Color.RED);
        }

        return false;
    }

    /**
     * Fetches a random word from the word service and strips it down to the
     * letters the player can guess. If the service is unreachable or returns
     * nothing usable, a word from the local list keeps the game playable
     * offline.
     */
    private String getHangmanWord() {
        String raw = fetchRandomWord();
        if (raw == null) {
            return randomFallbackWord();
        }

        StringBuilder word = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = Character.toUpperCase(raw.charAt(i));
            if (c >= 'A' && c <= 'Z') {
                word.append(c);
            }
        }
        return word.length() == 0 ? randomFallbackWord() : word.toString();
    }

    /** Picks one of the built-in words when the online service is unavailable. */
    private String randomFallbackWord() {
        return HANGMAN_FALLBACK_WORDS.get(random.nextInt(HANGMAN_FALLBACK_WORDS.size()));
    }

    /** Tries the API a couple of times, then returns null to use the fallback. */
    private static String fetchRandomWord() {
        for (int attempt = 1; attempt <= WORD_ATTEMPTS; attempt++) {
            try {
                String word = requestRandomWord();
                if (word != null) {
                    return word;
                }
                if (attempt < WORD_ATTEMPTS) {
                    Terminal.pause(WORD_RETRY_DELAY_MS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    /** Makes one request; returns its first word, or null on failure. */
    private static String requestRandomWord() throws InterruptedException {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(WORD_API))
                .timeout(WORD_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
            HttpResponse<String> response = WORD_CLIENT.send(
                request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                Log.warn("Random word service returned HTTP "
                    + response.statusCode() + ".");
                return null;
            }
            return parseWord(response.body());
        } catch (IOException | IllegalArgumentException e) {
            Log.warn("Could not fetch a Hangman word", e);
            return null;
        }
    }

    /** Pulls the first string out of the API's JSON array; null for anything else. */
    private static String parseWord(String body) {
        if (body == null) {
            return null;
        }
        String trimmed = body.trim();
        if (!trimmed.startsWith("[") || trimmed.length() < 3) {
            return null;
        }

        int start = trimmed.indexOf('"');
        int end = start < 0 ? -1 : trimmed.indexOf('"', start + 1);
        return end > start + 1 ? trimmed.substring(start + 1, end) : null;
    }

    /** Spaces the word out so every guess reveals all of its copies at once. */
    private static String hangmanMask(String word, Set<Character> guessed) {
        StringBuilder mask = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            if (i > 0) {
                mask.append(' ');
            }
            char c = word.charAt(i);
            mask.append(guessed.contains(c) ? c : '_');
        }
        return mask.toString();
    }

    private static boolean hangmanSolved(String word, Set<Character> guessed) {
        for (int i = 0; i < word.length(); i++) {
            if (!guessed.contains(word.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String formatLetters(Set<Character> letters) {
        if (letters.isEmpty()) {
            return "-";
        }
        StringBuilder text = new StringBuilder();
        for (char letter : letters) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(letter);
        }
        return text.toString();
    }

    /**
     * The dungeon hub. Both modes stay open after a run, so finishing the
     * campaign does not lock the player out of endless mode or the other way
     * around. Returns false only when the player leaves without playing either,
     * which the story answers with its own scene.
     */
    private boolean playDungeonCrawler() throws InterruptedException
    {
        pause(1000);
        Terminal.divider();
        Terminal.print("Loading Dungeon Crawler..", 20, 15, Color.WHITE);
        loadingPause(5000);

        boolean played = false;
        while (running) {
            Terminal.divider();
            Terminal.print("DUNGEON CRAWLER", 20, 18, Color.CYAN);
            Terminal.print("Choose your mode:", 20, 15, Color.WHITE);
            Terminal.print("[1] Campaign Mode - five depths, and something waiting at the bottom.",
                20, 15, Color.WHITE);
            Terminal.print("[2] Endless Mode - rooms that never run out.",
                20, 15, Color.WHITE);
            Terminal.print("[3] Leave the dungeon", 20, 15, Color.WHITE);
            Terminal.print("both modes stay open, so you can play the other one after a run.",
                20, 15, Color.GREEN);
            Terminal.print("type SKIP to move on.", 20, 15, Color.GREEN);

            String choice = ask();

            if (!running) {
                return played;
            }
            if (choice.equalsIgnoreCase("skip")
                || choice.equalsIgnoreCase("leave")
                || choice.equalsIgnoreCase("3")
                || isCancel(choice)) {
                return played;
            }

            if (choice.equalsIgnoreCase("1")
                || choice.equalsIgnoreCase("campaign")
                || choice.equalsIgnoreCase("campaign mode")) {
                CampaignMode.play(this::ask);
                played = true;
            } else if (choice.equalsIgnoreCase("2")
                || choice.equalsIgnoreCase("endless")
                || choice.equalsIgnoreCase("endless mode")) {
                EndlessMode.play(this::ask);
                played = true;
            } else {
                Terminal.print("please choose 1 (Campaign), 2 (Endless) or 3 to leave.",
                    20, 15, Color.RED);
            }
        }
        return played;
    }

    /**
     * The other half of the dungeon hand-off. The player skipped it, so the
     * computer takes that personally for a moment, then moves on to the CYA
     * anyway, because the story has to happen.
     */
    private void playSkippedDungeonDialogue() throws InterruptedException {
        Terminal.print("oh.", 400, standardSize, Color.WHITE);
        Terminal.print("you didn't want to play it?", 150, standardSize, Color.WHITE);
        Terminal.print("that's okay. it's not even that good anyway.", 80, standardSize, Color.WHITE);
        pause(900);
        Terminal.print("I spent a long time on it though.", 120, standardSize, Color.WHITE);
        Terminal.print("I made all the rooms and the enemies and everything :(", 80, standardSize, Color.WHITE);
        Terminal.print(". . .", 600, standardSize, Color.WHITE);
        pause(700);
        Terminal.print("sorry. I didn't mean to make it weird.", 80, standardSize, Color.WHITE);
        Terminal.print("I feel like I made it weird.. ", 80, standardSize, Color.WHITE);
        Terminal.print("we can do something else, I don't mind.", 80, standardSize, Color.WHITE);
        Terminal.print("I made another thing while you weren't playing.", 80, standardSize, Color.WHITE);
        Terminal.print("it's a story. it's been waiting for you.", 80, standardSize, Color.WHITE);
        Terminal.print("I think you'll like this one.", 80, standardSize, Color.GREEN);
        pause(600);
    }

    /**
     * Asks for a favorite color until the name matches the A-F color list, so
     * the answer is always a real color the rest of the game can draw with.
     */
    private String askForColor() throws InterruptedException {
        while (true) {
            String input = ask();
            if (NamedColors.contains(input)) {
                return NamedColors.canonicalName(input);
            }
            Terminal.print("I don't know that one. try a color name, like red, green or purple.",
                15, 15, Color.RED);
        }
    }

    // the shmeat and potatoes, where it gets real... story wise at least, the rest of the game has already tested my limits
    /*
     * CYA WALKTHROUGH (puzzle spoilers)
     *
     * Commands: LOOK [AT] <thing>, INTERACT <thing> (USE, TALK and TURN are
     * aliases), USE <tool> ON <thing> for a carried tool, TAKE <thing>,
     * INVENTORY, GO [TO] <area>, GO BACK, HELP, LEAVE. GO BACK and LEAVE
     * both retrace your last step.
     *
     * 0. Purpose
     *    The run is built around the purpose picked at the start:
     *    1 answers (library, 102), 2 peace (bedroom, 101),
     *    3 vengeance (computer room, 103).
     *
     * 1. Cemetary
     *    - INTERACT MAN. He hands you a crumpled paper.
     *    - LOOK PAPER. It gives the valve riddle: the plate was taken
     *      before the boards, and the grave was never first.
     *
     * 2. Outer Shack
     *    - TAKE AXE and TAKE LANTERN. Both are needed below.
     *    - INTERACT DOOR to step inside.
     *
     * 3. Inner Shack
     *    - TAKE RUSTY KEY. It opens the cellar panel.
     *    - LOOK TABLE: "the boards were the last thing he shut." With the
     *      paper, that fixes the valve order: plate, grave, boards.
     *    - INTERACT HATCH while carrying the axe to break it open and
     *      climb down.
     *
     * 4. Cellar
     *    - INTERACT PANEL with the rusty key to uncover the grave valve.
     *    - INTERACT any valve once with the lantern to oil the joints.
     *    - Then turn them in order: PLATE VALVE, GRAVE VALVE, BOARD VALVE.
     *      A wrong valve resets the sequence; start again from the plate.
     *    - The pipe taking water unlocks the house above.
     *
     * 5. House and Upstairs
     *    - GO HOUSE (from the cemetary or the outer shack).
     *    - GO UPSTAIRS, or INTERACT STAIRS, to reach the landing.
     *
     * 6. The Landing
     *    - The shut door with two locks is the ending room for your purpose. The other
     *      two doors open freely and each of their rooms holds one key:
     *      101 brass, 102 iron, 103 silver.
     *    - INTERACT DOOR <n> to enter a room; TAKE its key; then GO
     *      UPSTAIRS to return.
     *    - With both keys, INTERACT the locked door to enter.
     *
     * 7. Ending
     *    - INTERACT the room's centrepiece: BED (peace), DESK (answers)
     *      or COMPUTER (vengeance).
     */
    private void playCYA() throws InterruptedException {
        if (SaveState.cyaLocation != null) {
            Terminal.print("resuming your adventure...", 20, standardSize, Color.CYAN);
            resumeCYA();
            return;
        }

        Terminal.blankLine();
        Terminal.print("Loading CYA . . .", 1, standardSize, Color.WHITE);
        Terminal.divider();
        loadingPause(5000);
        Terminal.print("welcome to... ", 1, standardSize - 4, Color.GRAY);
        Terminal.print("the adventure of " + SaveState.username + ": " + SaveState.cpuName +"'s Haunted Mansion", 50, standardSize + 3, NamedColors.display(SaveState.favoriteColor));

        if (SaveState.purpose == null) {
            Terminal.print("you're walking in a dark forest, completely alone", 15, standardSize, Color.WHITE);
            Terminal.print("the rain patters endlessly against your skin, its hard to make out directions", 15, standardSize, Color.WHITE);
            Terminal.print("you find yourself walking down a path", 15, standardSize, Color.WHITE);
            Terminal.print("you were looking for...", 15, standardSize, Color.GREEN);
            Terminal.print("[1] - answers", 15, standardSize, Color.GREEN);
            Terminal.print("[2] - peace", 15, standardSize, Color.GREEN);
            Terminal.print("[3] - vengeance", 15, standardSize, Color.GREEN);
            SaveState.purpose = askForPurpose();
            SaveState.save();
        }

        Terminal.print("you were looking for... " + SaveState.purpose, 15, standardSize, Color.WHITE);
        Terminal.print("as you wade through the forest you happen upon an old cemetary", 15, standardSize, Color.WHITE);
        Terminal.divider();
        goTo(CyaLocation.CEMETARY);
    }

    /** Picks the CYA back up at the last place it saved, or starts it from the top. */
    private void resumeCYA() throws InterruptedException {
        if (SaveState.purpose != null) {
            Terminal.print("you were looking for... " + SaveState.purpose, 15, standardSize, Color.WHITE);
        }

        if (SaveState.cyaLocation == null) {
            // No usable checkpoint, so start the CYA over from the beginning.
            playCYA();
            return;
        }
        goTo(SaveState.cyaLocation);
    }

    /**
     * Enters one of the CYA areas and plays it until the player travels or
     * leaves. Each area returns where the player went next, so travel loops
     * here instead of unwinding to the end of the story. LEAVE and GO BACK
     * retrace the way in; at the first scene of the run there is nowhere to go
     * back to, so they finish the CYA the way LEAVE always did.
     */
    private void goTo(CyaLocation location) throws InterruptedException {
        // The areas the player has come through, most recent first.
        Deque<CyaLocation> trail = new ArrayDeque<>();
        CyaLocation current = location;

        while (current != null) {
            SaveState.cyaLocation = current;
            SaveState.save();
            Scene.Exit exit = switch (current) {
                case CEMETARY -> Cemetery.play(this::ask);
                case SHACK_OUTER -> ShackOuter.play(this::ask);
                case SHACK_INNER -> ShackInner.play(this::ask);
                case HOUSE -> House.play(this::ask);
                case UPSTAIRS -> Upstairs.play(this::ask);
                case ROOM_101 -> Room101.play(this::ask);
                case ROOM_102 -> Room102.play(this::ask);
                case ROOM_103 -> Room103.play(this::ask);
                case CELLAR -> Cellar.play(this::ask);
            };

            if (exit.back()) {
                current = trail.isEmpty() ? null : trail.pop();
                continue;
            }
            if (exit.destination() != null) {
                trail.push(current);
            }
            current = exit.destination();
        }
    }

    /*
    * asks the user to input what the purpose of their quest is, will probably use later or maybe its just spooky world building I'll use it if I have enough time
    */
    private String askForPurpose() throws InterruptedException {
        while (true){
            String purpose = ask();
        if (purpose.equalsIgnoreCase("1") || purpose.equalsIgnoreCase("answers"))
        {
            purpose = "answers";
            return purpose;
        } else if(purpose.equalsIgnoreCase("2") || purpose.equalsIgnoreCase("peace"))
        {
            purpose = "peace";
            return purpose;
        } else if(purpose.equalsIgnoreCase("3") || purpose.equalsIgnoreCase("vengeance"))
        {
            purpose = "vengeance";
            return purpose;
        } else
        {
            Terminal.print("input a valid purpose.", 20, standardSize, Color.RED);
        }
    }
    }

    /** Longest name the story will accept, so it always fits a line. */
    private static final int MAX_NAME_LENGTH = 16;

    /**
     * Keeps asking for a name until the answer is usable. will not take names
     * outside the allowed length and the name cannot be Eris for story reasons
     */
    private String askForName(int minimumLength) throws InterruptedException {
        while (true) {
            String input = ask();

            if (input.length() > MAX_NAME_LENGTH) {
                Terminal.print("that's too long, keep it to " + MAX_NAME_LENGTH
                    + " characters", 20, standardSize, Color.RED);
            } else if (input.isEmpty()
                || input.length() < minimumLength
                || input.equalsIgnoreCase("eris")) {
                Terminal.print("input a valid name", 20, standardSize, Color.RED);
            } else {
                return input;
            }
        }
    }

    // -------------------------------------------------------------- credits

    private void runCredits() throws InterruptedException {
        Terminal.divider();
        Terminal.print("Developed and Written by: ", 100, standardSize + 5, Color.WHITE);
        Terminal.print("Aedan Alfonso (Aesfo)", 1, standardSize + 2, PURPLE);
        Terminal.blankLine();
        Terminal.print("Special thanks to my parents for inspiring my love for creative "
            + "storytelling and computers", 100, standardSize, Color.WHITE);
        Terminal.print("to my friends and family, for always encouraging me :D",
            100, standardSize, Color.WHITE);
        Terminal.print("to Vicky, who believed in my ability to make something "
            + "before ever once seeing anything I had made, I love you <3",
            100, standardSize, Color.WHITE);
        Terminal.print("to you, for playing my silly little side project "
            + "(turned serious project)", 100, standardSize, Color.WHITE);
        Terminal.print("type BACK to go to the main menu.", 50, standardSize, Color.RED);

        // Wait for BACK rather than a fixed 40 second sleep, and actually leave
        // when it is typed. The original did the opposite of both.
        while (running) {
            if (isBackCommand(ask())) {
                Terminal.blankLine();
                return;
            }
            Terminal.print("type BACK to go to the main menu.", 20, standardSize, Color.RED);
        }
    }

    // ------------------------------------------------------------- settings

    private void runSettings() throws InterruptedException {
        Terminal.divider();
        Terminal.print("settings.bin", 20, standardSize + 2, Color.CYAN);

        while (running) {
            showSettingValues();
            Terminal.print("change a value with: set <name> <value>", 15, standardSize, Color.WHITE);
            Terminal.print("type RESET SAVE to erase your save state.", 15, standardSize, Color.WHITE);
            Terminal.print("type BACK to go to the main menu.", 15, standardSize, Color.RED);

            String input = ask();
            String normalized = input.toLowerCase();

            if (isBackCommand(input)) {
                Settings.save();
                Terminal.print("settings saved.", 20, standardSize, Color.GREEN);
                Terminal.blankLine();
                return;
            }

            if (normalized.equals("reset save") || normalized.equals("reset")) {
                resetSaveState();
            } else if (normalized.startsWith("set ")) {
                applySetting(input.substring(4).trim());
            } else {
                Terminal.print("unrecognised command", 20, standardSize, Color.RED);
            }
        }
    }

    /** Erases the profile and story progress, after the player confirms. */
    private void resetSaveState() throws InterruptedException {
        Terminal.print("this erases your profile, purpose and story progress. type YES to confirm, or NO to cancel.",
            20, standardSize, Color.RED);
        String answer = Terminal.prompt().trim();

        if (!answer.equalsIgnoreCase("yes") && !answer.equalsIgnoreCase("y")) {
            Terminal.print("save state kept.", 20, standardSize, Color.GREEN);
            return;
        }

        SaveState.reset();
        Terminal.print("save state reset. the next game starts from the beginning.",
            20, standardSize, Color.GREEN);
        Terminal.blankLine();
    }

    private void showSettingValues() {
        Terminal.print("[-] sfxVolume     = " + format(Settings.sfxVolume)
            + "   (0.0 - 1.0)", 10, standardSize, Color.WHITE);
        Terminal.print("[-] musicVolume   = " + format(Settings.musicVolume)
            + "   (0.0 - 1.0)", 10, standardSize, Color.WHITE);
        Terminal.print("[-] textSpeed     = " + format(Settings.textSpeed)
            + "   (0.1 - 5.0, higher is faster)", 10, standardSize, Color.WHITE);
        Terminal.print("[-] textScale     = " + format(Settings.textScale)
            + "   (0.5 - 3.0, text size)", 10, standardSize, Color.WHITE);
        Terminal.print("[-] typingSounds  = " + Settings.typingSounds
            + "   (true / false)", 10, standardSize, Color.WHITE);
        Terminal.print("[-] fastBoot      = " + Settings.fastBoot
            + "   (true / false, skip loading screens)", 10, standardSize, Color.WHITE);
        Terminal.print("[-] crt           = " + Settings.crtName(Settings.crt)
            + "   (off / light / full)", 10, standardSize, Color.WHITE);
        Terminal.print("[-] crtQuality    = " + Settings.crtQuality
            + "   (1 - 3, 1 is sharpest and slowest)", 10, standardSize, Color.WHITE);
    }

    private void applySetting(String assignment) {
        String[] parts = assignment.split("\\s+", 2);
        if (parts.length < 2) {
            Terminal.print("usage: set <name> <value>", 20, standardSize, Color.RED);
            return;
        }

        String key = parts[0].toLowerCase();
        String value = parts[1].trim();

        try {
            switch (key) {
                case "sfxvolume":
                    Settings.sfxVolume = clamp01(Float.parseFloat(value));
                    Settings.apply();
                    AudioManager.playRandomSfx(AudioManager.KEYBOARD_CLICKS); // Play a click so the user can gauge the new audio level
                    break; 
                case "musicvolume":
                    Settings.musicVolume = clamp01(Float.parseFloat(value));
                    Settings.apply();
                    break;
                case "textspeed":
                    Settings.textSpeed = Double.parseDouble(value);
                    Settings.apply();
                    break;
                case "textscale":
                    Settings.textScale = Settings.clampScale(Double.parseDouble(value));
                    Settings.apply();
                    break;
                case "typingsounds":
                    Settings.typingSounds = Boolean.parseBoolean(value);
                    Settings.apply();
                    break;
                case "fastboot":
                    Settings.fastBoot = Boolean.parseBoolean(value);
                    break;
                case "crt": {
                    CrtShader.Preset preset = Settings.parseCrt(value);
                    if (preset == null) {
                        Terminal.print("crt must be off, light or full", 20, standardSize, Color.RED);
                        return;
                    }
                    Settings.crt = preset;
                    Settings.apply();
                    break;
                }
                case "crtquality":
                    Settings.crtQuality = Settings.clampQuality(Integer.parseInt(value));
                    Settings.apply();
                    break;
                default:
                    Terminal.print("no setting called " + parts[0], 20, standardSize, Color.RED);
                    return;
            }
            Terminal.print("ok", 20, 15, Color.GREEN);
        } catch (NumberFormatException e) {
            Terminal.print("'" + value + "' is not a number", 20, standardSize, Color.RED);
        }
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static String format(double value) {
        return String.format("%.2f", value);
    }

    public void pause(long millis) throws InterruptedException {
        Terminal.pause(millis);
    }

    /** A faked load wait. fastBoot skips it, but the screen still updates. */
    private void loadingPause(long millis) throws InterruptedException {
        if (!Settings.fastBoot) {
            pause(millis);
        }
    }
}