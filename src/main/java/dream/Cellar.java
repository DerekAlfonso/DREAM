package dream;

import java.util.List;

/**
 * The room under the shack's floor, reached through the broken-open hatch.
 *
 * <p>The puzzle is the three valves on the pipe. None of them will move until
 * the lantern's oil is worked into the joints, the third one stays hidden behind
 * a padlocked panel until the rusty key opens it, and they only let the water
 * through when they are turned in the right order. That order is not written
 * down here: it has to be worked out from the man's note in the graveyard and a
 * sentence scratched into the shack's table. Solving it unlocks the house.
 */
final class Cellar {

    /**
     * The order the valves must be turned in: the plate, then the grave, then the
     * boards. The note rules the grave out of first place, and the table says the
     * boards were shut last.
     */
    private static final List<String> ORDER = List.of("plateValve", "graveValve", "boardValve");

    /**
     * Valves opened so far in the right order. A wrong one sets them all back, and
     * the count is not saved, so reloading always starts the sequence again.
     */
    private static int opened = 0;

    private static final List<Interactable> OBJECTS = List.of(
        Interactable.builder("cellarHatch")
            .names("hatch", "ladder", "way up", "ceiling hatch")
            .look("The hatch above you is a pale square of light. The ladder nailed beneath it still holds.")
            .interact("You climb the ladder and push back up through the hatch.")
            .travelTo(() -> CyaLocation.SHACK_INNER)
            .refuseTake("The hatch is part of the shack. Taking it would be a very short trip.")
            .build(),
        Interactable.builder("plateValve")
            .names("plate valve", "plate", "brass valve")
            .look("A brass valve is bolted to the pipe, its handle worn bright where hands have worked it. "
                + "Stamped into the brass is a plate, set for one.")
            .interact(() -> turnValve("plateValve"))
            .refuseTake("The valve is bolted through the pipe. You would have to take the pipe with it.")
            .build(),
        Interactable.builder("boardValve")
            .names("board valve", "board", "boards", "iron valve")
            .look("An iron valve, heavier than the other, stamped with a board and a nail driven through it.")
            .interact(() -> turnValve("boardValve"))
            .refuseTake("The valve is bolted through the pipe. It is not going anywhere.")
            .build(),
        Interactable.builder("graveValve")
            .names("grave valve", "grave", "stone", "leaning stone")
            .look("A small valve, half eaten by rust, stamped with a leaning headstone.")
            .interact(() -> turnValve("graveValve"))
            .refuseTake("The valve is bolted to the pipe, behind a panel that has its own opinions.")
            .hiddenWhen(() -> !SaveState.panelOpen)
            .build(),
        Interactable.builder("ironPanel")
            .names("panel", "iron panel", "padlock", "lock")
            .look(Cellar::describePanel)
            .interact(Cellar::openPanel)
            .refuseTake("The panel is part of the wall. The padlock, though, is tempting.")
            .build()
    );

    private Cellar() { }

    static List<Interactable> objects() {
        return OBJECTS;
    }

    static Scene.Exit play(Scene.Input input) throws InterruptedException {
        return new Scene(CyaLocation.CELLAR, "The Cellar Beneath the Shack",
            Cellar::describeArea, OBJECTS).play(input);
    }

    /**
     * Clears valve progress that was never saved, so a new run starts with
     * no valves open even when the same process just played another sequence.
     */
    static void resetProgress() {
        opened = 0;
    }

    /** Built fresh each time, so the panel reads as open once the key has been in it. */
    private static List<TextSpan> describeArea() {
        String description = "A low brick room under the shack's floor, cold and close, with the hatch above "
            + "you letting in a pale square of light. A rusted pipe climbs out of the floor and into the wall, "
            + "and two valves are bolted to it in a row, one stamped with a plate and one with a board.";
        description += SaveState.panelOpen
            ? " The iron panel at the foot of the pipe hangs open on its hinge."
            : " An iron panel at the foot of the pipe is held shut by a brass padlock.";
        if (SaveState.cellarPuzzleSolved) {
            description += " The pipe is silent now, and somewhere above, the house is awake.";
        }
        return TextSpan.highlighting(description, Scene.NAME, "plate", "board", "panel");
    }

    private static String describePanel() {
        if (SaveState.panelOpen) {
            return "The iron panel stands open on its hinge. The third valve is bolted to the pipe inside "
                + "it, stamped with a leaning stone.";
        }
        return "An iron panel is set into the brick at the foot of the pipe, held shut by a brass padlock "
            + "with no key in it. The lock is small and old, the kind a small key would open.";
    }

    /** Opens the panel that hides the third valve, if the player has the rusty key. */
    private static String openPanel() {
        if (SaveState.panelOpen) {
            return "The panel is already open. The third valve waits inside it.";
        }
        if (!SaveState.hasItem("rustyKey")) {
            return "You tug at the padlock. It is small, and old, and does not care. A small key would open it.";
        }
        SaveState.panelOpen = true;
        SaveState.save();
        return "The rusty key turns in the padlock with a click that sounds almost relieved. The panel "
            + "swings open, and inside, bolted to the pipe, is a third valve stamped with a leaning stone.";
    }

    /**
     * Turns one valve. The oil has to be in the joints first, and the valves only
     * stay open when they are turned in the order the clues describe.
     */
    private static String turnValve(String id) {
        if (SaveState.cellarPuzzleSolved) {
            return "The valves sit open and the pipe is silent. The house above has already answered.";
        }
        if (!SaveState.valvesOiled) {
            if (!SaveState.hasItem("rustyLantern")) {
                return "You put both hands on the valve and lean until your arms shake. It will not move: the "
                    + "rust has seized the joint. A little oil worked into it would free it.";
            }
            SaveState.valvesOiled = true;
            SaveState.save();
            return "You tip the lantern over the valves and work the oil into the joints with your thumb. "
                + "The rust softens, and the handle shifts a fraction under your hand.";
        }

        if (!ORDER.get(opened).equals(id)) {
            opened = 0;
            return "You haul the valve round. Water slams shut somewhere in the pipe, the other valves "
                + "shudder on their bolts, and everything settles back the way it was. The sequence has "
                + "restarted. Begin again at the plate valve.";
        }

        opened++;
        if (opened < ORDER.size()) {
            return "The valve turns with a grinding shriek. Water moves somewhere above you, and the handle "
                + "goes slack in your hand, like the pipe is waiting for the next one.";
        }

        opened = 0;
        SaveState.cellarPuzzleSolved = true;
        SaveState.save();
        return "You turn the last valve, and the pipe takes water with a sound like a long swallow. Far "
            + "above you, muffled by the floorboards, something heavy falls over. It came from the house.";
    }
}
