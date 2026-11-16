package dream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * What the AI remembers about the player between runs. Written to saveState.txt,
 * which the original created but never actually used.
 */
public final class SaveState {

    private static final Path FILE = UserData.file("saveState.txt");

    /**
     * Written with every save. Bump this when a field changes meaning, and add
     * the migration to {@link #load()}; older saves must keep loading.
     */
    public static final int SAVE_VERSION = 2;

    public static String username = null;
    public static String cpuName = null;
    public static String favoriteColor = null;
    public static String purpose = null;

    /** The last CYA place reached, or null if the CYA has not reached one yet. */
    public static CyaLocation cyaLocation = null;

    /** How far the main story has got. Null means a new game. */
    public static String stage = null;

    /** Profile done, but rock-paper-scissors is still in progress. */
    public static final String STAGE_RPS = "rps";

    /** Rock-paper-scissors done, hangman in progress. */
    public static final String STAGE_HANGMAN = "hangman";

    /** Hangman done, the dungeon crawler in progress. */
    public static final String STAGE_DUNGEON = "dungeon";

    /** Minigames done; the CYA is in progress or waiting to start. */
    public static final String STAGE_CYA = "cya";

    /** What version 1 called the whole minigame block. */
    private static final String LEGACY_STAGE_MINIGAMES = "minigames";

    private SaveState() { }

    public static void load() {
        if (!Files.isRegularFile(FILE)) {
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            props.load(in);
        } catch (IOException e) {
            Log.warn("Could not read saveState.txt", e);
            return;
        }

        int version = readInt(props, "saveVersion", 1);

        username = emptyToNull(props.getProperty("username"));
        cpuName = emptyToNull(props.getProperty("cpuName"));
        favoriteColor = emptyToNull(props.getProperty("favoriteColor"));
        purpose = emptyToNull(props.getProperty("purpose"));
        cyaLocation = CyaLocation.fromId(props.getProperty("CYALocation"));

        String loadedStage = emptyToNull(props.getProperty("stage"));
        if (version < 2 && LEGACY_STAGE_MINIGAMES.equals(loadedStage)) {
            loadedStage = STAGE_RPS;
        }
        stage = normalizeStage(loadedStage);

        if (username != null) {
            Log.info("Loaded save state for " + username);
        }
    }

    public static void save() {
        Properties props = new Properties();
        props.setProperty("saveVersion", String.valueOf(SAVE_VERSION));
        props.setProperty("username", nullToEmpty(username));
        props.setProperty("cpuName", nullToEmpty(cpuName));
        props.setProperty("favoriteColor", nullToEmpty(favoriteColor));
        props.setProperty("purpose", nullToEmpty(purpose));
        props.setProperty("CYALocation", cyaLocation == null ? "" : cyaLocation.id);
        props.setProperty("stage", nullToEmpty(stage));

        try {
            UserData.createParent(FILE);
        } catch (IOException e) {
            Log.warn("Could not create save state directory", e);
            return;
        }
        try (OutputStream out = Files.newOutputStream(FILE)) {
            props.store(out, "D.R.E.A.M save state");
        } catch (IOException e) {
            Log.warn("Could not write saveState.txt", e);
        }
    }

    public static boolean hasProfile() {
        return username != null && cpuName != null;
    }

    public static void reset() {
        username = null;
        cpuName = null;
        favoriteColor = null;
        purpose = null;
        cyaLocation = null;
        stage = null;
        save();
    }

    /** Keeps only known stages, so a hand-edited save cannot skip the story. */
    private static String normalizeStage(String value) {
        if (value == null) {
            return null;
        }
        switch (value) {
            case STAGE_RPS:
            case STAGE_HANGMAN:
            case STAGE_DUNGEON:
            case STAGE_CYA:
                return value;
            default:
                return null;
        }
    }

    private static int readInt(Properties props, String key, int fallback) {
        try {
            return Integer.parseInt(props.getProperty(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
