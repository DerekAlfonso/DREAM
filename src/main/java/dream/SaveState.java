package dream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

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

    /** True once the player has spoken to the man at the cemetary. */
    public static boolean spokenToMysteriousMan = false;

    /** True once the axe has broken open the shack's floor hatch. */
    public static boolean hatchBroken = false;

    /**
     * True once the rusty lantern's oil has been worked into the cellar's valves.
     * The rust has to soften before any of them will turn.
     */
    public static boolean valvesOiled = false;

    /** True once the rusty key has opened the cellar panel hiding the third valve. */
    public static boolean panelOpen = false;

    /**
     * True once the cellar's iron wheel has been turned far enough. This is
     * also what unlocks the house.
     */
    public static boolean cellarPuzzleSolved = false;

    /**
     * The inventory: ids of the objects the player has taken, in the order
     * they were picked up. Saved with the rest of the run.
     */
    public static final Set<String> takenObjects = new LinkedHashSet<>();

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
        spokenToMysteriousMan = Boolean.parseBoolean(
            props.getProperty("spokenToMysteriousMan", "false"));
        hatchBroken = Boolean.parseBoolean(props.getProperty("hatchBroken", "false"));
        valvesOiled = Boolean.parseBoolean(props.getProperty("valvesOiled", "false"));
        panelOpen = Boolean.parseBoolean(props.getProperty("panelOpen", "false"));
        cellarPuzzleSolved = Boolean.parseBoolean(
            props.getProperty("cellarPuzzleSolved", "false"));
        takenObjects.clear();
        String taken = props.getProperty("takenObjects", "");
        for (String id : taken.split(",")) {
            if (!id.trim().isEmpty()) {
                takenObjects.add(id.trim());
            }
        }

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
        props.setProperty("spokenToMysteriousMan", String.valueOf(spokenToMysteriousMan));
        props.setProperty("hatchBroken", String.valueOf(hatchBroken));
        props.setProperty("valvesOiled", String.valueOf(valvesOiled));
        props.setProperty("panelOpen", String.valueOf(panelOpen));
        props.setProperty("cellarPuzzleSolved", String.valueOf(cellarPuzzleSolved));
        props.setProperty("takenObjects", String.join(",", takenObjects));
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

    /** True if this save's inventory contains the object with this id. */
    public static boolean hasItem(String id) {
        return takenObjects.contains(id);
    }

    /** Adds an object id to this save's inventory and writes it immediately. */
    public static void addItem(String id) {
        takenObjects.add(id);
        save();
    }

    public static void reset() {
        username = null;
        cpuName = null;
        favoriteColor = null;
        purpose = null;
        cyaLocation = null;
        spokenToMysteriousMan = false;
        hatchBroken = false;
        valvesOiled = false;
        panelOpen = false;
        cellarPuzzleSolved = false;
        Cellar.resetProgress();
        takenObjects.clear();
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
