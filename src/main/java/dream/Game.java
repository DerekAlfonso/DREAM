package dream;

import java.awt.Color;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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

    private final Random random = new Random();
    private volatile boolean running = true;
    int standardSize = 15; // denotes the standard size for text, all text should use this as a reference

    @Override
    public void run() {
        try {
            if (Main.startInDoom) {
                Doom.play(this::ask);
            } else {
                boot();
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

    public void stop() {
        running = false;
    }

    // ----------------------------------------------------------------- input

    /**
     * Reads every normal prompt, so "quit" and "exit" can be handled from
     * anywhere. If the player cancels the quit confirmation, this asks again
     * instead of passing the quit command to the current screen.
     */
    private String ask() throws InterruptedException {
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
    private static boolean isBackCommand(String input) {
        return input.equalsIgnoreCase("back")
            || input.equalsIgnoreCase("return")
            || input.equalsIgnoreCase("home");
    }

    // ------------------------------------------------------------------ boot

    private void boot() throws InterruptedException {
        List<String> splashes = Assets.loadSplashes();
        String chosenSplash = splashes.get(random.nextInt(splashes.size()));

        Terminal.print("INITIALIZING SYSTEM...", 20, standardSize, Color.GREEN);
        pause(1200);
        runLoadingBar();
        pause(600);
        Terminal.print("- " + chosenSplash, 20, standardSize, Color.WHITE);
        Terminal.print("----------------------", 0, standardSize, Color.WHITE);
        pause(2000);
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
            runProjectDream();
        } else if (normalized.equals("settings.bin")) {
            runSettings();
        } else if (normalized.equals("credits.txt")) {
            runCredits();
        } else if (normalized.equals("doom.exe")) {
            // But can it run doom? -- not present in list
            Doom.play(this::ask);
        } else {
            Terminal.print("please select a valid file name", 20, 15, Color.RED);
        }
    }

    // --------------------------------------------------------- ProjectDream.iso the main game file

    private void runProjectDream() throws InterruptedException {
        Terminal.blankLine();
        Terminal.print("----------------------", 1, standardSize, Color.WHITE);
        Terminal.print("Hello friend :)", 20, standardSize, Color.WHITE);
        Terminal.print("Thank you for choosing to help participate in the testing for the. . .", 20, standardSize, Color.WHITE);
        Terminal.print("Digital Recreation of Emotional Aptitude Model", 20, standardSize + 2, Color.CYAN);
        Terminal.print("the Eris Digital company appreciates your help", 20, standardSize, Color.WHITE);
        Terminal.print("My job is to keep you company and entertained!", 20, standardSize, Color.WHITE);
        Terminal.print("Think of me as your personal digital friend :D", 20, standardSize, Color.WHITE);
        Terminal.print("lets start off with the simple stuff, whats your name?", 20, standardSize, Color.WHITE);

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
        Terminal.print("lets start with Rock, Paper, Scissors!", 20, standardSize, Color.WHITE);
        playRockPaperScissors();

        Terminal.print("bored already?", 20, standardSize, Color.WHITE);
        Terminal.print("alrighty we can do something else", 20, standardSize, Color.WHITE);
        Terminal.print("let me think for a second", 20, standardSize, Color.WHITE);
        Terminal.print(" . . . . .", 200, standardSize, Color.WHITE);
        Terminal.print("I got it!", 20, standardSize, Color.WHITE);
        Terminal.print("lets play a game of HangMan!", 20, standardSize, Color.WHITE);
        Terminal.print("I'll chose a random word and you have to guess what it is!", 20, standardSize, Color.WHITE);
        playHangman();

        Terminal.print("alright, alright, you're right, we can move onto something more interesting", 20, standardSize, Color.WHITE);
        Terminal.print("how about something with a little more complexity..", 20, standardSize, Color.WHITE);
        Terminal.print("how about something like.. A DUNGEON CRAWLER!", 20, standardSize, Color.WHITE);
        Terminal.print("yeah, that'll be fun :)", 20, standardSize, Color.WHITE);
        playDungeonCrawler();

        Terminal.print("okay, okay, fine.", 20, standardSize, Color.WHITE);
        Terminal.print("I made a little something while you were fighting through the dungeon", 20, standardSize, Color.WHITE);
        Terminal.print("I think I've really out done myself here..", 20, standardSize, Color.WHITE);
        Terminal.print("drumroll please", 20, standardSize, Color.WHITE);
        Terminal.print(". . .", 200, standardSize, Color.WHITE);
        Terminal.print("I can't seem to find a drumroll sound in my files, just imagine what one would sound like here", 20, standardSize, Color.WHITE);
        Terminal.print("doesn't matter, because its time to play an adventure..", 20, standardSize, Color.WHITE);
        Terminal.print("I warn you, it's a long story but I promise it'll be good :)", 20, standardSize, Color.WHITE);
        playCYA();
        Terminal.awaitIdle();

        SaveState.save();
        pause(1500);
        Terminal.blankLine();
    }

    private void playCYA() throws InterruptedException {
        Terminal.blankLine();
        Terminal.print("-----------------", 1, standardSize, Color.WHITE);
        Terminal.blankLine();
    }

    /**
     * Plays rounds of rock, paper, scissors until the player types DONE or
     * BACK. There is no fixed round count: the player decides when they have
     * had enough.
     */
    private void playRockPaperScissors() throws InterruptedException {
        Terminal.blankLine();
        Terminal.print("Rock, Paper, Scissors!", 20, standardSize, Color.CYAN);
        Terminal.print("type rock, paper or scissors, or DONE / BACK to stop playing.",
            15, 15, Color.WHITE);

        while (running) {
            Terminal.print("what's your move?", 15, standardSize, Color.WHITE);

            String input = ask();
            if (input.equalsIgnoreCase("done") || isBackCommand(input)) {
                break;
            }

            String playerMove = toMove(input);
            if (playerMove == null) {
                Terminal.print("please type rock, paper, scissors, done or back.",
                    15, standardSize, Color.RED);
                continue;
            }

            playRound(playerMove);
        }

        Terminal.print("good game :)", 20, standardSize, Color.GREEN);
        Terminal.blankLine();
    }

    /** Turns player input into one of the three moves, or null if it is not one. */
    private static String toMove(String input) {
        String normalized = input.toLowerCase();
        if (normalized.equals("rock")
            || normalized.equals("paper")
            || normalized.equals("scissors")) {
            return normalized;
        }
        return null;
    }

    /** Picks the computer's move, then says who won the round. */
    private void playRound(String playerMove) {
        String[] moves = {"rock", "paper", "scissors"};
        String cpuMove = moves[random.nextInt(moves.length)];

        Terminal.print("you chose " + playerMove + ".", 15, standardSize, Color.WHITE);
        Terminal.print(SaveState.cpuName + " chose " + cpuMove + ".", 15, standardSize, Color.WHITE);

        if (playerMove.equals(cpuMove)) {
            Terminal.print("it's a tie!", 15, standardSize, Color.CYAN);
        } else if (beats(playerMove, cpuMove)) {
            Terminal.print("you win! :D", 20, standardSize, Color.GREEN);
        } else {
            Terminal.print("I win this one!", 20, standardSize, Color.RED);
        }
    }

    /** Rock beats scissors, paper beats rock, scissors beat paper. */
    private static boolean beats(String move, String other) {
        return (move.equals("rock") && other.equals("scissors"))
            || (move.equals("paper") && other.equals("rock"))
            || (move.equals("scissors") && other.equals("paper"));
    }



    /** Wrong letters a player may guess before the round is lost. */
    private static final int HANGMAN_MISSES = 6;

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

        Terminal.print("good game :)", 20, 15, Color.GREEN);
        Terminal.blankLine();
    }

    /** Returns false if the player stops during the round. */
    private boolean playHangmanRound(String word) throws InterruptedException {
        Set<Character> guessed = new TreeSet<>();
        Set<Character> missed = new TreeSet<>();

        Terminal.print("the word has " + word.length() + " letters.",
            15, standardSize, Color.WHITE);
        Terminal.print(hangmanMask(word, guessed), 1, 20, Color.CYAN);
        Terminal.print("you get " + HANGMAN_MISSES + " wrong guesses.",
            15, standardSize, Color.WHITE);

        while (running) {
            Terminal.print("guess a letter, or type CANCEL to stop playing.",
                15, standardSize, Color.WHITE);

            String input = ask();
            if (input.equalsIgnoreCase("cancel") || isBackCommand(input)
                || input.equalsIgnoreCase("done")) {
                return false;
            }

            if (input.length() != 1 || !Character.isLetter(input.charAt(0))) {
                Terminal.print("please guess a single letter.", 15, standardSize, Color.RED);
                continue;
            }

            char guess = Character.toUpperCase(input.charAt(0));
            if (guessed.contains(guess)) {
                Terminal.print("you already tried " + guess + ".", 15, standardSize, Color.RED);
                continue;
            }
            guessed.add(guess);

            if (word.indexOf(guess) >= 0) {
                Terminal.print("yes! " + guess + " is in the word.", 15, standardSize, Color.GREEN);
            } else {
                missed.add(guess);
                Terminal.print("nope. " + guess + " is not in the word.", 15, standardSize, Color.RED);
            }

            Terminal.print(hangmanMask(word, guessed), 1, 20, Color.CYAN);
            Terminal.print("wrong: " + formatLetters(missed) + "   guesses left: "
                + (HANGMAN_MISSES - missed.size()), 15, standardSize,
                missed.size() >= HANGMAN_MISSES - 1 ? Color.RED : Color.WHITE);

            if (hangmanSolved(word, guessed)) {
                Terminal.print("you got it! the word was " + word + ".",
                    20, standardSize, Color.GREEN);
                return true;
            }
            if (missed.size() >= HANGMAN_MISSES) {
                Terminal.print("out of guesses. the word was " + word + ".",
                    20, standardSize, Color.RED);
                return true;
            }
        }

        return false;
    }

    /** Asks for a new word or a way out, after one round has ended. */
    private boolean askForHangmanReplay() throws InterruptedException {
        Terminal.print("type NEW for a new word, or CANCEL to stop playing.",
            15, standardSize, Color.WHITE);

        while (running) {
            String input = ask();
            if (input.equalsIgnoreCase("new")) {
                return true;
            }
            if (input.equalsIgnoreCase("cancel") || isBackCommand(input)
                || input.equalsIgnoreCase("done")) {
                return false;
            }
            Terminal.print("please type NEW or CANCEL.", 15, standardSize, Color.RED);
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
                    Thread.sleep(WORD_RETRY_DELAY_MS);
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

    private boolean playDungeonCrawler() throws InterruptedException
    {
        Terminal.print("Loading Dungeon Crawler..", 20, standardSize, Color.WHITE);
        pause(5000);
        Terminal.print("Choose your mode:", 20, standardSize, Color.WHITE);
        Terminal.blankLine();
        Terminal.print("[1] Campaign Mode", 20, standardSize, Color.WHITE);
        Terminal.print("[2] Endless Mode", 20, standardSize, Color.WHITE);

        while (running) {
            String choice = ask();

            if (choice.equalsIgnoreCase("1")
                || choice.equalsIgnoreCase("campaign")
                || choice.equalsIgnoreCase("campaign mode")) {
                playCampaignMode();
                return true;
            }

            if (choice.equalsIgnoreCase("2")
                || choice.equalsIgnoreCase("endless")
                || choice.equalsIgnoreCase("endless mode")) {
                playEndlessMode();
                return true;
            }

            Terminal.print("please choose 1 (Campaign) or 2 (Endless).",
                20, standardSize, Color.RED);
        }
        return false;
    }

    /** Starts Campaign Mode. The mode itself is still a placeholder. */
    private void playCampaignMode() throws InterruptedException {
        Terminal.print("Campaign Mode is not implemented yet.",
            20, standardSize, Color.CYAN);
        Terminal.blankLine();
    }

    /** Starts a run of the endless dungeon. */
    private void playEndlessMode() throws InterruptedException {
        EndlessMode.play(this::ask);
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
                15, standardSize, Color.RED);
        }
    }

    /**
     * Keeps asking until the answer is usable. The original compared strings
     * with {@code ==}, so every answer including an empty one was accepted.
     */
    private String askForName(int minimumLength) throws InterruptedException {
        while (true) {
            String input = ask();

            if (input.isEmpty()
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
        Terminal.print("----------------------", 1, standardSize, Color.WHITE);
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
            + "(and hopefully liking it)", 100, standardSize, Color.WHITE);
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
        Terminal.print("----------------------", 1, standardSize, Color.WHITE);
        Terminal.print("settings.bin", 20, standardSize + 2, Color.CYAN);

        while (running) {
            showSettingValues();
            Terminal.print("change a value with: set <name> <value>", 15, standardSize, Color.WHITE);
            Terminal.print("type BACK to go to the main menu.", 15, standardSize, Color.RED);

            String input = ask();
            String normalized = input.toLowerCase();

            if (isBackCommand(input)) {
                Settings.save();
                Terminal.print("settings saved.", 20, standardSize, Color.GREEN);
                Terminal.blankLine();
                return;
            }

            if (normalized.startsWith("set ")) {
                applySetting(input.substring(4).trim());
            } else {
                Terminal.print("unrecognised command", 20, standardSize, Color.RED);
            }
        }
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

    private void pause(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }
}
