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

    public static String username = null;
    public static String cpuName = null;
    public static String favoriteColor = null;
    public static String purpose = null;
    public static String CYALocation = null;

    /** How far the main story has got. Null means a new game. */
    public static String stage = null;

    /** Profile done, but the minigames are still in progress. */
    public static final String STAGE_MINIGAMES = "minigames";

    /** Minigames done; the CYA is in progress or waiting to start. */
    public static final String STAGE_CYA = "cya";

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

        username = emptyToNull(props.getProperty("username"));
        cpuName = emptyToNull(props.getProperty("cpuName"));
        favoriteColor = emptyToNull(props.getProperty("favoriteColor"));
        purpose = emptyToNull(props.getProperty("purpose"));
        CYALocation = emptyToNull(props.getProperty("CYALocation"));
        stage = emptyToNull(props.getProperty("stage"));
        if (username != null) {
            Log.info("Loaded save state for " + username);
        }
    }

    public static void save() {
        Properties props = new Properties();
        props.setProperty("username", nullToEmpty(username));
        props.setProperty("cpuName", nullToEmpty(cpuName));
        props.setProperty("favoriteColor", nullToEmpty(favoriteColor));
        props.setProperty("purpose", nullToEmpty(purpose));
        props.setProperty("CYALocation", nullToEmpty(CYALocation));
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
        CYALocation = null;
        stage = null;
        save();
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
