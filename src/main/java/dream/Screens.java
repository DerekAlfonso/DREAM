package dream;

import java.awt.Color;

/** The settings and credits screens from the boot menu. */
final class Screens {

    private static final Color PURPLE = new Color(141, 20, 222);

    private final Game game;

    Screens(Game game) {
        this.game = game;
    }

    // ------------------------------------------------------------- settings

    void settings() throws InterruptedException {
        Terminal.print("----------------------", 1, Game.standardSize, Color.WHITE);
        Terminal.print("settings.bin", 20, Game.standardSize + 2, Color.CYAN);

        while (game.isRunning()) {
            showSettingValues();
            Terminal.print("change a value with: set <name> <value>", 15, Game.standardSize, Color.WHITE);
            Terminal.print("type HELP for commands and aliases.", 15, Game.standardSize, Color.WHITE);
            Terminal.print("type RESET SAVE to erase your save state.", 15, Game.standardSize, Color.WHITE);
            Terminal.print("type BACK to go to the main menu.", 15, Game.standardSize, Color.RED);

            String input = game.ask();
            String normalized = input.toLowerCase();

            if (Game.isBackCommand(input)) {
                Settings.save();
                Terminal.print("settings saved.", 20, Game.standardSize, Color.GREEN);
                Terminal.blankLine();
                return;
            }

            if (normalized.equals("help")) {
                showHelp();
            } else if (normalized.equals("reset save") || normalized.equals("reset")) {
                resetSaveState();
            } else if (normalized.startsWith("set ")) {
                applySetting(input.substring(4).trim());
            } else {
                Terminal.print("unrecognised command", 20, Game.standardSize, Color.RED);
            }
        }
    }

    private void showSettingValues() {
        Terminal.print("[-] sfxVolume     = " + format(Settings.sfxVolume)
            + "   (0.0 - 1.0)", 10, Game.standardSize, Color.WHITE);
        Terminal.print("[-] musicVolume   = " + format(Settings.musicVolume)
            + "   (0.0 - 1.0)", 10, Game.standardSize, Color.WHITE);
        Terminal.print("[-] textSpeed     = " + format(Settings.textSpeed)
            + "   (0.1 - 5.0, higher is faster)", 10, Game.standardSize, Color.WHITE);
        Terminal.print("[-] textScale     = " + format(Settings.textScale)
            + "   (0.5 - 3.0, text size)", 10, Game.standardSize, Color.WHITE);
        Terminal.print("[-] typingSounds  = " + Settings.typingSounds
            + "   (true / false)", 10, Game.standardSize, Color.WHITE);
        Terminal.print("[-] fastBoot      = " + Settings.fastBoot
            + "   (true / false, skip loading screens)", 10, Game.standardSize, Color.WHITE);
        Terminal.print("[-] crt           = " + Settings.crtName(Settings.crt)
            + "   (off / light / full)", 10, Game.standardSize, Color.WHITE);
        Terminal.print("[-] crtQuality    = " + Settings.crtQuality
            + "   (1 - 3, 1 is sharpest and slowest)", 10, Game.standardSize, Color.WHITE);
    }

    private void showHelp() {
        Terminal.print("set <name> <value>   change a setting", 15, Game.standardSize, Color.WHITE);
        Terminal.print("settings: sfxVolume, musicVolume, textSpeed, textScale,",
            15, Game.standardSize, Color.WHITE);
        Terminal.print("          typingSounds, fastBoot, crt, crtQuality",
            15, Game.standardSize, Color.WHITE);
        Terminal.print("aliases:  sfx, music, speed, scale, sound",
            15, Game.standardSize, Color.WHITE);
        Terminal.print("reset save           erase your profile and story progress",
            15, Game.standardSize, Color.WHITE);
        Terminal.print("back                 return to the main menu",
            15, Game.standardSize, Color.WHITE);
        Terminal.print("during the story, type menu to save and return here.",
            15, Game.standardSize, Color.WHITE);
    }

    private void applySetting(String assignment) {
        String[] parts = assignment.split("\\s+", 2);
        if (parts.length < 2) {
            Terminal.print("usage: set <name> <value>", 20, Game.standardSize, Color.RED);
            return;
        }

        String key = parts[0].toLowerCase();
        String value = parts[1].trim();

        try {
            switch (key) {
                case "sfx":
                case "sfxvolume":
                    Settings.sfxVolume = clamp01(Float.parseFloat(value));
                    Settings.apply();
                    AudioManager.playRandomSfx(AudioManager.KEYBOARD_CLICKS); // Play a click so the user can gauge the new audio level
                    break;
                case "music":
                case "musicvolume":
                    Settings.musicVolume = clamp01(Float.parseFloat(value));
                    Settings.apply();
                    break;
                case "speed":
                case "textspeed":
                    Settings.textSpeed = Settings.clampSpeed(Double.parseDouble(value));
                    Settings.apply();
                    break;
                case "scale":
                case "textscale":
                    Settings.textScale = Settings.clampScale(Double.parseDouble(value));
                    Settings.apply();
                    break;
                case "sound":
                case "typingsounds": {
                    Boolean on = parseOnOff(value);
                    if (on == null) {
                        Terminal.print("typingSounds must be true or false",
                            20, Game.standardSize, Color.RED);
                        return;
                    }
                    Settings.typingSounds = on;
                    Settings.apply();
                    break;
                }
                case "fastboot": {
                    Boolean on = parseOnOff(value);
                    if (on == null) {
                        Terminal.print("fastBoot must be true or false",
                            20, Game.standardSize, Color.RED);
                        return;
                    }
                    Settings.fastBoot = on;
                    break;
                }
                case "crt": {
                    CrtShader.Preset preset = Settings.parseCrt(value);
                    if (preset == null) {
                        Terminal.print("crt must be off, light or full",
                            20, Game.standardSize, Color.RED);
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
                    Terminal.print("no setting called " + parts[0] + " (type HELP)",
                        20, Game.standardSize, Color.RED);
                    return;
            }
            Terminal.print("ok", 20, 15, Color.GREEN);
        } catch (NumberFormatException e) {
            Terminal.print("'" + value + "' is not a number", 20, Game.standardSize, Color.RED);
        }
    }

    /** Erases the profile and story progress, after the player confirms. */
    private void resetSaveState() throws InterruptedException {
        Terminal.print("this erases your profile, purpose and story progress. type YES to confirm, or NO to cancel.",
            20, Game.standardSize, Color.RED);
        String answer = Terminal.prompt().trim();

        if (!answer.equalsIgnoreCase("yes") && !answer.equalsIgnoreCase("y")) {
            Terminal.print("save state kept.", 20, Game.standardSize, Color.GREEN);
            return;
        }

        SaveState.reset();
        Terminal.print("save state reset. the next game starts from the beginning.",
            20, Game.standardSize, Color.GREEN);
        Terminal.blankLine();
    }

    /** Accepts true/false as well as the on/off and yes/no forms. */
    private static Boolean parseOnOff(String value) {
        String normalized = value.toLowerCase();
        if (normalized.equals("true") || normalized.equals("on")
            || normalized.equals("yes") || normalized.equals("1")) {
            return Boolean.TRUE;
        }
        if (normalized.equals("false") || normalized.equals("off")
            || normalized.equals("no") || normalized.equals("0")) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static String format(double value) {
        return String.format("%.2f", value);
    }

    // -------------------------------------------------------------- credits

    void credits() throws InterruptedException {
        Terminal.print("----------------------", 1, Game.standardSize, Color.WHITE);
        Terminal.print("Developed and Written by: ", 100, Game.standardSize + 5, Color.WHITE);
        Terminal.print("Aedan Alfonso (Aesfo)", 1, Game.standardSize + 2, PURPLE);
        Terminal.blankLine();
        Terminal.print("Special thanks to my parents for inspiring my love for creative "
            + "storytelling and computers", 100, Game.standardSize, Color.WHITE);
        Terminal.print("to my friends and family, for always encouraging me :D",
            100, Game.standardSize, Color.WHITE);
        Terminal.print("to Vicky, who believed in my ability to make something "
            + "before ever once seeing anything I had made, I love you <3",
            100, Game.standardSize, Color.WHITE);
        Terminal.print("to you, for playing my silly little side project "
            + "(and hopefully liking it)", 100, Game.standardSize, Color.WHITE);
        Terminal.print("type BACK to go to the main menu.", 50, Game.standardSize, Color.RED);

        // Wait for BACK rather than a fixed 40 second sleep, and actually leave
        // when it is typed. The original did the opposite of both.
        while (game.isRunning()) {
            if (Game.isBackCommand(game.ask())) {
                Terminal.blankLine();
                return;
            }
            Terminal.print("type BACK to go to the main menu.", 20, Game.standardSize, Color.RED);
        }
    }
}
