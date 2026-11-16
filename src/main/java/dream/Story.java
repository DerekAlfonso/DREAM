package dream;

import java.awt.Color;

/**
 * The ProjectDream story: the profile questions, the three minigames and the
 * CYA. Holds the stage and checkpoint bookkeeping that lets a run resume.
 */
final class Story {

    private final Game game;
    private final Minigames minigames;

    Story(Game game, Minigames minigames) {
        this.game = game;
        this.minigames = minigames;
    }

    /** Plays (or resumes) the whole main story. */
    void play() throws InterruptedException {
        Terminal.blankLine();
        Terminal.print("----------------------", 1, Game.standardSize, Color.WHITE);

        if (SaveState.stage == null && !SaveState.hasProfile()) {
            Terminal.print("Hello friend :)", 20, Game.standardSize, Color.WHITE);
            Terminal.print("Thank you for choosing to help participate in the testing for the. . .", 20, Game.standardSize, Color.WHITE);
            Terminal.print("Digital Recreation of Emotional Aptitude Model", 20, Game.standardSize + 2, Color.CYAN);
            Terminal.print("the Eris Digital company appreciates your help", 20, Game.standardSize, Color.WHITE);
            Terminal.print("My job is to keep you company and entertained!", 20, Game.standardSize, Color.WHITE);
            Terminal.print("Think of me as your personal digital friend :D", 20, Game.standardSize, Color.WHITE);
            Terminal.print("lets start off with the simple stuff, whats your name?", 20, Game.standardSize, Color.WHITE);

            SaveState.username = askForName(2);
            Terminal.print(SaveState.username + ", huh?", 20, Game.standardSize, Color.WHITE);
            Terminal.print("I like that name!", 20, Game.standardSize, Color.WHITE);
            Terminal.print("Now I need a name. . . ", 20, Game.standardSize, Color.WHITE);
            Terminal.print("what should I be called?", 20, Game.standardSize, Color.WHITE);

            SaveState.cpuName = askForName(1);
            Terminal.print(SaveState.cpuName + "?", 200, Game.standardSize, Color.WHITE);
            Terminal.print(". . .", 500, Game.standardSize, Color.WHITE);
            Terminal.print("thats nice.", 200, Game.standardSize, Color.WHITE);
            Terminal.print("Okay one last question before the fun part..", 20, Game.standardSize, Color.WHITE);
            Terminal.print("what's your favorite color?", 20, Game.standardSize, Color.WHITE);

            SaveState.favoriteColor = askForColor();
            Terminal.print(SaveState.favoriteColor + "? Good choice :3", 20, Game.standardSize, NamedColors.display(SaveState.favoriteColor));
            Terminal.print("Okay now on to the fun part!", 20, Game.standardSize, Color.WHITE);
            Terminal.print("I want to get to know you better", 20, Game.standardSize, Color.WHITE);
            Terminal.print("but I can't just ask you questions all day (even if I want to)", 20, Game.standardSize, Color.WHITE);
            Terminal.print("so lets play a few games together :)", 20, Game.standardSize, Color.WHITE);

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
            Terminal.print("welcome back, " + SaveState.username + " :3", 20, Game.standardSize, Color.GREEN);
            Terminal.print("lets continue where we left off, with games!",
                20, Game.standardSize, Color.WHITE);
        }

        if (SaveState.STAGE_RPS.equals(SaveState.stage)) {
            Terminal.print("lets start with Rock, Paper, Scissors!", 20, Game.standardSize, Color.WHITE);
            minigames.rockPaperScissors();

            SaveState.stage = SaveState.STAGE_HANGMAN;
            SaveState.save();
        }

        if (SaveState.STAGE_HANGMAN.equals(SaveState.stage)) {
            Terminal.print("bored already?", 20, Game.standardSize, Color.WHITE);
            Terminal.print("alrighty we can do something else", 20, Game.standardSize, Color.WHITE);
            Terminal.print("let me think for a second", 20, Game.standardSize, Color.WHITE);
            Terminal.print(" . . . . .", 200, Game.standardSize, Color.WHITE);
            Terminal.print("I got it!", 20, Game.standardSize, Color.WHITE);
            Terminal.print("lets play a game of HangMan!", 20, Game.standardSize, Color.WHITE);
            Terminal.print("I'll chose a random word and you have to guess what it is!", 20, Game.standardSize, Color.WHITE);
            minigames.hangman();

            SaveState.stage = SaveState.STAGE_DUNGEON;
            SaveState.save();
        }

        if (SaveState.STAGE_DUNGEON.equals(SaveState.stage)) {
            Terminal.print("alright, alright, you're right, we can move onto something more interesting", 20, Game.standardSize, Color.WHITE);
            Terminal.print("how about something with a little more complexity..", 20, Game.standardSize, Color.WHITE);
            Terminal.print("how about something like.. A DUNGEON CRAWLER!", 20, Game.standardSize, Color.WHITE);
            game.pause(500);
            Terminal.print("yeah, that'll be fun :)", 20, Game.standardSize, Color.WHITE);
            minigames.dungeonCrawler();

            SaveState.stage = SaveState.STAGE_CYA;
            SaveState.save();

            Terminal.print("okay, okay, fine.", 20, Game.standardSize, Color.WHITE);
            Terminal.print("I made a little something while you were fighting through the dungeon", 20, Game.standardSize, Color.WHITE);
            Terminal.print("I think I've really out done myself here..", 20, Game.standardSize, Color.WHITE);
            Terminal.print("drumroll please", 20, Game.standardSize, Color.WHITE);
            Terminal.print(". . .", 600, Game.standardSize, Color.WHITE);
            Terminal.print("I can't seem to find a drumroll sound in my files, just imagine what one would sound like here", 20, Game.standardSize, Color.WHITE);
            Terminal.print("doesn't matter, because its time to play an adventure..", 20, Game.standardSize, Color.WHITE);
            Terminal.print("I warn you, it's a long story but I promise it'll be good :)", 20, Game.standardSize, Color.WHITE);
        }

        playCYA();

        Terminal.awaitIdle();
        SaveState.save();
        game.pause(1500);
        Terminal.blankLine();
    }

    // the shmeat and potatoes, where it gets real... story wise at least, the rest of the game has already tested my limits
    private void playCYA() throws InterruptedException {
        if (SaveState.cyaLocation != null) {
            Terminal.print("resuming your adventure...", 20, Game.standardSize, Color.CYAN);
            resumeCYA();
            return;
        }

        Terminal.blankLine();
        Terminal.print("Loading CYA . . .", 1, Game.standardSize, Color.WHITE);
        game.loadingPause(5000);
        Terminal.print("-----------------", 1, Game.standardSize, Color.WHITE);
        Terminal.blankLine();
        Terminal.print("welcome to... ", 1, Game.standardSize - 4, Color.GRAY);
        Terminal.print("the adventure of " + SaveState.username + ": " + SaveState.cpuName + "'s Haunted Mansion", 50, Game.standardSize + 3, NamedColors.display(SaveState.favoriteColor));

        if (SaveState.purpose == null) {
            Terminal.print("you're walking in a dark forest, completely alone", 15, Game.standardSize, Color.WHITE);
            Terminal.print("the rain patters endlessly against your skin, its hard to make out directions", 15, Game.standardSize, Color.WHITE);
            Terminal.print("you find yourself walking down a path", 15, Game.standardSize, Color.WHITE);
            Terminal.print("you were looking for...", 15, Game.standardSize, Color.GREEN);
            Terminal.blankLine();
            Terminal.print("[1] - answers", 15, Game.standardSize, Color.GREEN);
            Terminal.print("[2] - peace", 15, Game.standardSize, Color.GREEN);
            Terminal.print("[3] - vengeance", 15, Game.standardSize, Color.GREEN);
            SaveState.purpose = askForPurpose();
            SaveState.save();
        }

        Terminal.print("you were looking for... " + SaveState.purpose, 15, Game.standardSize, Color.WHITE);
        Terminal.print("as you wade through the forest you happen upon an old cemetary", 15, Game.standardSize, Color.WHITE);
        goToCemetary();
    }

    /** Picks the CYA back up at the last place it saved, or starts it from the top. */
    private void resumeCYA() throws InterruptedException {
        if (SaveState.purpose != null) {
            Terminal.print("you were looking for... " + SaveState.purpose, 15, Game.standardSize, Color.WHITE);
        }

        CyaLocation location = SaveState.cyaLocation;
        if (location == null) {
            return;
        }
        switch (location) {
            case CEMETARY:
                goToCemetary();
                break;
            case SHACK_OUTER:
                goToDecrepitShackOuter();
                break;
            case SHACK_INNER:
                goToDecrepitShackInner();
                break;
            case HOUSE:
                goToHouse();
                break;
        }
    }

    private void goToCemetary() throws InterruptedException {
        Terminal.print("cemetary gameplay", 15, Game.standardSize, Color.RED);
        checkpoint(CyaLocation.CEMETARY);
    }

    private void goToDecrepitShackOuter() throws InterruptedException {
        Terminal.print("Outer shack gameplay", 15, Game.standardSize, Color.RED);
        checkpoint(CyaLocation.SHACK_OUTER);
    }

    private void goToDecrepitShackInner() throws InterruptedException {
        Terminal.print("Inner Shack gameplay", 15, Game.standardSize, Color.RED);
        checkpoint(CyaLocation.SHACK_INNER);
    }

    private void goToHouse() throws InterruptedException {
        Terminal.print("house (no not the doctor from the TV show) gameplay", 15, Game.standardSize, Color.RED);
        checkpoint(CyaLocation.HOUSE);
    }

    /** Records one CYA checkpoint in a single place, so saves cannot be missed. */
    private static void checkpoint(CyaLocation location) {
        SaveState.cyaLocation = location;
        SaveState.stage = SaveState.STAGE_CYA;
        SaveState.save();
    }

    /*
     * asks the user to input what the purpose of their quest is, will probably use later or maybe its just spooky world building I'll use it if I have enough time
     */
    private String askForPurpose() throws InterruptedException {
        while (true) {
            String purpose = game.ask();
            if (purpose.equalsIgnoreCase("1") || purpose.equalsIgnoreCase("answers")) {
                return "answers";
            } else if (purpose.equalsIgnoreCase("2") || purpose.equalsIgnoreCase("peace")) {
                return "peace";
            } else if (purpose.equalsIgnoreCase("3") || purpose.equalsIgnoreCase("vengeance")) {
                return "vengeance";
            } else {
                Terminal.print("input a valid purpose.", 20, Game.standardSize, Color.RED);
            }
        }
    }

    /**
     * Keeps asking for a name until the answer is usable. will not take names under a certain length and the name cannot be Eris for story reasons
     */
    private String askForName(int minimumLength) throws InterruptedException {
        while (true) {
            String input = game.ask();

            if (input.isEmpty()
                || input.length() < minimumLength
                || input.equalsIgnoreCase("eris")) {
                Terminal.print("input a valid name", 20, Game.standardSize, Color.RED);
            } else {
                return input;
            }
        }
    }

    /**
     * Asks for a favorite color until the name matches the A-F color list, so
     * the answer is always a real color the rest of the game can draw with.
     */
    private String askForColor() throws InterruptedException {
        while (true) {
            String input = game.ask();
            if (NamedColors.contains(input)) {
                return NamedColors.canonicalName(input);
            }
            Terminal.print("I don't know that one. try a color name, like red, green or purple.",
                15, 15, Color.RED);
        }
    }
}
