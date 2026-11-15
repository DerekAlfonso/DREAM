package dream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Player-adjustable options, stored as plain text. Backs the settings.bin
 * entry on the boot menu.
 */
public final class Settings {

    private static final Path FILE = UserData.file("settings.txt");

    public static float sfxVolume = 0.7f;
    public static float musicVolume = 0.5f;
    public static double textSpeed = 1.0;
    public static boolean typingSounds = true;

    /**
     * Extra text magnification on top of the automatic scaling the terminal
     * derives from the screen height. 1.0 leaves the automatic size alone.
     */
    public static double textScale = 1.0;

    /** How much CRT filtering the software shader applies. */
    public static CrtShader.Preset crt = CrtShader.Preset.OFF;

    /**
     * Filter resolution, 1-3. 1 filters at full resolution; 2 and 3 filter at
     * 1.5x and 2x downsampling, which softens the text.
     */
    public static int crtQuality = 1;

    private Settings() { }

    public static void load() {
        Properties props = new Properties();
        if (Files.isRegularFile(FILE)) {
            try (InputStream in = Files.newInputStream(FILE)) {
                props.load(in);
            } catch (IOException e) {
                Log.warn("Could not read settings.txt", e);
            }
        }

        sfxVolume = readFloat(props, "sfxVolume", sfxVolume);
        musicVolume = readFloat(props, "musicVolume", musicVolume);
        textSpeed = readDouble(props, "textSpeed", textSpeed);
        textScale = clampScale(readDouble(props, "textScale", textScale));
        typingSounds = Boolean.parseBoolean(
            props.getProperty("typingSounds", String.valueOf(typingSounds)));

        CrtShader.Preset loadedCrt = parseCrt(props.getProperty("crt", crtName(crt)));
        crt = loadedCrt == null ? CrtShader.Preset.OFF : loadedCrt;
        crtQuality = clampQuality(readInt(props, "crtQuality", crtQuality));

        apply();
    }

    public static void save() {
        Properties props = new Properties();
        props.setProperty("sfxVolume", String.valueOf(sfxVolume));
        props.setProperty("musicVolume", String.valueOf(musicVolume));
        props.setProperty("textSpeed", String.valueOf(textSpeed));
        props.setProperty("textScale", String.valueOf(textScale));
        props.setProperty("typingSounds", String.valueOf(typingSounds));
        props.setProperty("crt", crtName(crt));
        props.setProperty("crtQuality", String.valueOf(crtQuality));

        try {
            UserData.createParent(FILE);
        } catch (IOException e) {
            Log.warn("Could not create settings directory", e);
            return;
        }
        try (OutputStream out = Files.newOutputStream(FILE)) {
            props.store(out, "D.R.E.A.M settings");
        } catch (IOException e) {
            Log.warn("Could not write settings.txt", e);
        }
    }

    /** Pushes the current values into the systems that use them. */
    public static void apply() {
        AudioManager.setSfxVolume(sfxVolume);
        AudioManager.setMusicVolume(musicVolume);
        Terminal.setSpeedMultiplier(textSpeed);
        Terminal.setTypingSounds(typingSounds);
        // Text size may have changed, so recentre and rescale what is on screen.
        Terminal.relayout();
        // The filter settings may have changed, so drop the cached frame.
        CurveCanvas.refreshCrt();
    }

    public static double clampScale(double value) {
        return Math.max(0.5, Math.min(3.0, value));
    }

    public static int clampQuality(int value) {
        return Math.max(1, Math.min(3, value));
    }

    /** Parses off / light / full. Returns null for anything else. */
    public static CrtShader.Preset parseCrt(String raw) {
        if (raw == null) {
            return null;
        }
        switch (raw.trim().toLowerCase()) {
            case "off":
                return CrtShader.Preset.OFF;
            case "light":
                return CrtShader.Preset.LIGHT;
            case "full":
                return CrtShader.Preset.FULL;
            default:
                return null;
        }
    }

    public static String crtName(CrtShader.Preset preset) {
        return preset.name().toLowerCase();
    }

    private static float readFloat(Properties props, String key, float fallback) {
        try {
            return Float.parseFloat(props.getProperty(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double readDouble(Properties props, String key, double fallback) {
        try {
            return Double.parseDouble(props.getProperty(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int readInt(Properties props, String key, int fallback) {
        try {
            return Integer.parseInt(props.getProperty(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
