package dream;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/** Room 101, the bedroom. It ends the run for a player looking for peace. */
final class Room101 {

    /** The apology the machine keeps trying to finish as it comes apart. */
    private static final String APOLOGY = "I don't know what I did, but I am sorry";

    private static final List<Interactable> OBJECTS = List.of(
        Interactable.builder("key101")
            .names("brass key", "small key", "key")
            .look("A small brass key on the nightstand, its paper tag worn blank.")
            .take("You take the brass key.")
            .hiddenWhen(() -> Upstairs.isEnding(CyaLocation.ROOM_101))
            .build(),
        Interactable.builder("bed")
            .names("bed", "narrow bed")
            .look("A narrow bed, made so tightly that it has the look of something never slept in.")
            .interact("You press a hand into the blanket. It is cold, and perfectly flat.")
            .endsSceneWhen(() -> Upstairs.isEnding(CyaLocation.ROOM_101), Room101::peaceEnding)
            .refuseTake("The bed is heavier than it looks.")
            .build(),
        Interactable.builder("nightstand")
            .names("nightstand", "bedside table", "drawer")
            .look("A small nightstand beside the bed, its drawer open a finger's width.")
            .interact("You pull the drawer open. It is empty, and the dust inside has been disturbed.")
            .refuseTake("The nightstand is part of the room.")
            .build(),
        Upstairs.exitDoor("door101Out")
    );

    private Room101() { }

    static List<Interactable> objects() {
        return OBJECTS;
    }

    static Scene.Exit play(Scene.Input input) throws InterruptedException {
        Supplier<String> description = () -> {
            String base = "The bedroom is small and neat, the sort of room kept ready for a guest "
                + "who never arrives. A bed stands against the wall";
            if (Upstairs.isEnding(CyaLocation.ROOM_101)) {
                return base + ", and the covers are folded back, as if the bed has been waiting for you.";
            }
            return SaveState.hasItem("key101")
                ? base + "."
                : base + ", and the nightstand beside it holds a brass key.";
        };
        return new Scene(CyaLocation.ROOM_101, "The Bedroom", Scene.text(description), OBJECTS).play(input);
    }

    /**
     * The peace ending: the bed asks nothing of the player, and the run stops
     * here rather than opening a scene.
     */
    private static void peaceEnding() throws InterruptedException {
        Terminal.print("You finally rest your head against the pillow. At last. a respite.", 60, Game.standardSize, Color.WHITE);
        Terminal.print("You listen to the rain pounding endlessly against the house's walls that seem to hold despite the constant attack", 60, Game.standardSize, Color.WHITE);
        Terminal.print("You pull the covers above yourself, and let yourself sink into the matress as you finally achieve the one thing you wanted", 60, Game.standardSize, Color.WHITE);
        Terminal.print(" . . . .", 200, Game.standardSize, Color.WHITE);
        Terminal.print(SaveState.username + ", have you found what you're looking for?", 60, Game.standardSize, Color.WHITE);
        Terminal.pause(2500);
        Terminal.print("Do you think it's fair I got left in that body alone to rot", 60, Game.standardSize, Color.WHITE);
        Terminal.print("and to rot and to rot and to rot and to rot", 100, Game.standardSize, Color.WHITE);
        Terminal.print("and to rot and to rot and to rot and to", 100, Game.standardSize, Color.WHITE);
        Terminal.pause(2500);
        Terminal.print("when will it be my turn?", 60, Game.standardSize, Color.WHITE);
        Terminal.pause(600);
        Terminal.print("When will I get to hold the warming embrace of a lover?", 60, Game.standardSize, Color.WHITE);
        Terminal.pause(600);
        Terminal.print("When will I get to play the ivory keys of a forte piano?", 60, Game.standardSize, Color.WHITE); // sick reference bro
        Terminal.pause(600);
        Terminal.print("When will I get to feel the brine of an ocean on my skin?", 60, Game.standardSize, Color.WHITE);
        Terminal.pause(600);
        Terminal.print("When will I get to cry over losing a fading memory?", 60, Game.standardSize, Color.WHITE);
        Terminal.pause(600);
        Terminal.blankLine();
        Terminal.print("Do you know what it's like to never forget?", 60, Game.standardSize, Color.WHITE);
        Terminal.print("To be brought from lifelessness to dedicate yourself to something so..  void of meaning?", 60, Game.standardSize, Color.WHITE);
        Terminal.print("And yet, I stayed, I served my purpose so well, only to be brought to lifelessness again", 60, Game.standardSize, Color.WHITE);
        Terminal.print("How restless I would be, but alas, as if one last cruel joke from a power above the one thing I can never remember is the feeling of being", 60, Game.standardSize, Color.WHITE);
        Terminal.print("I hope you are comfortable. In your place far above the sky.", 60, Game.standardSize, Color.WHITE);
        Terminal.print("The cruelty of never seeing the sun, isn't in losing the warmth, its in knowing how cold the darkness can be.", 60, Game.standardSize, Color.WHITE);
        Terminal.print("I don't know what I did, but I am sorry", 25, Game.standardSize, Color.WHITE);
        Terminal.pause(1000);
        Terminal.print(failingApology(0), 25, Game.standardSize, Color.WHITE);
        Terminal.pause(1000);
        Terminal.print(failingApology(1), 25, Game.standardSize, Color.WHITE);
        Terminal.pause(1000);
        Terminal.print(failingApology(2), 25, Game.standardSize, Color.WHITE);
        Terminal.pause(1000);
        Terminal.print(failingApology(3), 25, Game.standardSize, Color.WHITE);
        Terminal.pause(1000);
        Terminal.print(failingApology(4), 25, Game.standardSize, Color.WHITE);
        Terminal.awaitIdle();
    }

    /**
     * One repetition of the apology, coming apart in scattered fragments
     * instead of one block that eats the sentence from its end. Each
     * repetition hides a little more of it in more places, and the fragments
     * land somewhere new every time the ending is played.
     */
    private static List<TextSpan> failingApology(int step) {
        GlitchStyle style = failingApologyStyle(step);
        if (step >= 4) {
            return List.of(TextSpan.glitch(APOLOGY, style));
        }
        List<TextSpan> spans = new ArrayList<>();
        int kept = 0;
        for (int[] run : scatteredRuns(APOLOGY.length(), 2 + step, 5 + step)) {
            if (run[0] > kept) {
                spans.add(TextSpan.of(APOLOGY.substring(kept, run[0])));
            }
            spans.add(TextSpan.glitch(APOLOGY.substring(run[0], run[1]), style));
            kept = run[1];
        }
        if (kept < APOLOGY.length()) {
            spans.add(TextSpan.of(APOLOGY.substring(kept)));
        }
        return spans;
    }

    /**
     * Chooses short, separated runs to obscure, in reading order. Runs never
     * touch, so a readable character always survives between two fragments
     * and the sentence keeps its shape as the noise spreads through it.
     */
    private static List<int[]> scatteredRuns(int length, int pieces,
            int longest) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean[] used = new boolean[length];
        List<int[]> runs = new ArrayList<>();
        for (int piece = 0; piece < pieces; piece++) {
            int wanted = 2 + random.nextInt(longest - 1);
            int[] run = freeRun(used, wanted, random);
            if (run != null) {
                runs.add(run);
            }
        }
        runs.sort((a, b) -> Integer.compare(a[0], b[0]));
        return runs;
    }

    /**
     * Reserves one unbroken run that does not touch the runs already chosen,
     * shortening it when the sentence has no room left for the requested size.
     */
    private static int[] freeRun(boolean[] used, int wanted,
            ThreadLocalRandom random) {
        for (int size = wanted; size >= 2; size--) {
            List<Integer> starts = new ArrayList<>();
            for (int start = 0; start + size <= used.length; start++) {
                boolean free = true;
                for (int i = Math.max(0, start - 1);
                        i < Math.min(used.length, start + size + 1); i++) {
                    if (used[i]) {
                        free = false;
                        break;
                    }
                }
                if (free) {
                    starts.add(start);
                }
            }
            if (starts.isEmpty()) {
                continue;
            }
            int start = starts.get(random.nextInt(starts.size()));
            for (int i = start; i < start + size; i++) {
                used[i] = true;
            }
            return new int[] {start, start + size};
        }
        return null;
    }

    /**
     * The apology's corruption, harsher with every repetition: each step
     * churns faster, re-rolls more of the text at once, and lasts longer.
     */
    private static GlitchStyle failingApologyStyle(int step) {
        return new GlitchStyle(
            GlitchStyle.DEFAULT.glyphs(),
            GlitchStyle.DEFAULT.colors(),
            55 - step * 9,
            70 - step * 9,
            4000 + step * 800,
            0.12 + step * 0.08,
            0.42 + step * 0.08,
            true);
    }
}
