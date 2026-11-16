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

/** The rock-paper-scissors, hangman and dungeon-crawler screens. */
final class Minigames {

    private final Game game;
    private final Random random = new Random();

    Minigames(Game game) {
        this.game = game;
    }

    /**
     * Plays rounds of rock, paper, scissors until the player types DONE or
     * BACK. There is no fixed round count: the player decides when they have
     * had enough.
     */
    void rockPaperScissors() throws InterruptedException {
        Terminal.blankLine();
        Terminal.print("Rock, Paper, Scissors!", 20, 15, Color.CYAN);
        Terminal.print("type rock, paper or scissors, or DONE / BACK to stop playing.",
            15, 15, Color.WHITE);

        while (game.isRunning()) {
            Terminal.print("what's your move?", 15, 15, Color.WHITE);

            String input = game.ask();
            if (input.equalsIgnoreCase("done") || Game.isBackCommand(input)) {
                break;
            }

            String playerMove = toMove(input);
            if (playerMove == null) {
                Terminal.print("please type rock, paper, scissors, done or back.",
                    15, 15, Color.RED);
                continue;
            }

            playRound(playerMove);
        }

        Terminal.print("good game :)", 20, 15, Color.GREEN);
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
    void hangman() throws InterruptedException {
        boolean keepPlaying = true;

        while (game.isRunning() && keepPlaying) {
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
            15, 15, Color.WHITE);
        Terminal.print(hangmanMask(word, guessed), 1, 20, Color.CYAN);
        Terminal.print("you get " + HANGMAN_MISSES + " wrong guesses.",
            15, 15, Color.WHITE);

        while (game.isRunning()) {
            Terminal.print("guess a letter, or type CANCEL to stop playing.",
                15, 15, Color.WHITE);

            String input = game.ask();
            if (input.equalsIgnoreCase("cancel") || Game.isBackCommand(input)
                || input.equalsIgnoreCase("done")) {
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
                + (HANGMAN_MISSES - missed.size()), 15, 15,
                missed.size() >= HANGMAN_MISSES - 1 ? Color.RED : Color.WHITE);

            if (hangmanSolved(word, guessed)) {
                Terminal.print("you got it! the word was " + word + ".",
                    20, 15, Color.GREEN);
                return true;
            }
            if (missed.size() >= HANGMAN_MISSES) {
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

        while (game.isRunning()) {
            String input = game.ask();
            if (input.equalsIgnoreCase("new")) {
                return true;
            }
            if (input.equalsIgnoreCase("cancel") || Game.isBackCommand(input)
                || input.equalsIgnoreCase("done")) {
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

    boolean dungeonCrawler() throws InterruptedException
    {
        Terminal.print("Loading Dungeon Crawler..", 20, 15, Color.WHITE);
        game.loadingPause(5000);
        Terminal.print("Choose your mode:", 20, 15, Color.WHITE);
        Terminal.blankLine();
        Terminal.print("[1] Campaign Mode", 20, 15, Color.WHITE);
        Terminal.print("[2] Endless Mode", 20, 15, Color.WHITE);

        while (game.isRunning()) {
            String choice = game.ask();

            if (choice.equalsIgnoreCase("1")
                || choice.equalsIgnoreCase("campaign")
                || choice.equalsIgnoreCase("campaign mode")) {
                campaignMode();
                return true;
            }

            if (choice.equalsIgnoreCase("2")
                || choice.equalsIgnoreCase("endless")
                || choice.equalsIgnoreCase("endless mode")) {
                endlessMode();
                return true;
            }

            Terminal.print("please choose 1 (Campaign) or 2 (Endless).",
                20, 15, Color.RED);
        }
        return false;
    }

    /** Starts Campaign Mode. The mode itself is still a placeholder. */
    private void campaignMode() throws InterruptedException {
        Terminal.print("Campaign Mode is not implemented yet.",
            20, 15, Color.CYAN);
        Terminal.blankLine();
    }

    /** Starts a run of the endless dungeon. */
    private void endlessMode() throws InterruptedException {
        EndlessMode.play(game::ask);
    }
}
