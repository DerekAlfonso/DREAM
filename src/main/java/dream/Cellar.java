package dream;

import java.util.List;

/**
 * The room under the shack's floor, reached through the broken-open hatch.
 *
 * <p>The puzzle is the seized iron wheel on the pipe: it needs the axe for
 * leverage and seven turns, which the tally scratched into the brick spells
 * out. Solving it is what unlocks the house.
 */
final class Cellar {

    /** Turns the wheel needs before the pipe gives. The tally on the wall is the clue. */
    private static final int TURNS_NEEDED = 7;

    /** Turns so far this visit. Leaving and coming back resets it rather than punishing. */
    private static int turns = 0;

    private static final List<Interactable> OBJECTS = List.of(
        Interactable.builder("cellarHatch")
            .names("hatch", "ladder", "way up", "ceiling hatch")
            .look("The hatch above you is a pale square of light. The ladder nailed beneath it still holds.")
            .interact("You climb the ladder and push back up through the hatch.")
            .travelTo(() -> CyaLocation.SHACK_INNER)
            .refuseTake("The hatch is part of the shack. Taking it would be a very short trip.")
            .build(),
        Interactable.builder("tally")
            .names("tally", "scratches", "brick", "wall")
            .look("Seven strokes are scratched deep into the brick: three, a gap, three, a gap, one. "
                + "Underneath, in a smaller hand: \"turn it as many times as the walls remember.\"")
            .interact("You run a finger along the scratches. Whoever cut them pressed hard enough to chip the brick.")
            .refuseTake("You can't take the wall. You have tried harder things tonight.")
            .build(),
        Interactable.builder("ironWheel")
            .names("iron wheel", "wheel", "valve", "rusted wheel")
            .look("A heavy iron wheel is bolted onto a pipe that climbs out of the floor and into the wall. "
                + "Seven spokes, all of them orange with rust. It would turn, if you had something to lever it with.")
            .interact(Cellar::turnWheel)
            .refuseTake("The wheel is bolted to the pipe, and the pipe is bolted to the house.")
            .build()
    );

    private Cellar() { }

    static List<Interactable> objects() {
        return OBJECTS;
    }

    static CyaLocation play(Scene.Input input) throws InterruptedException {
        return new Scene(CyaLocation.CELLAR, "The Cellar Beneath the Shack",
            Cellar::describeArea, OBJECTS).play(input);
    }

    /** Built fresh each time, so a solved room stops sounding like it is waiting. */
    private static List<TextSpan> describeArea() {
        String description = "A low brick room under the shack's floor, cold and close, with the hatch above you "
            + "letting in a pale square of light. A rusted pipe climbs out of the floor and into the wall, "
            + "and someone has scratched a tally into the brick beside it.";
        if (SaveState.cellarPuzzleSolved) {
            description += " The pipe is silent now, and somewhere above, the house is awake.";
        }
        return TextSpan.highlighting(description, "tally", Scene.NAME);
    }

    /** Turns the wheel if the player has the axe, and says what happened. */
    private static String turnWheel() {
        if (SaveState.cellarPuzzleSolved) {
            return "The wheel sits where you left it, spinning free. The house above has already answered.";
        }
        if (!SaveState.hasItem("axe")) {
            return "You get both hands on the wheel and pull until your arms shake. The rust does not give. "
                + "You would need something to lever the spokes with.";
        }

        turns++;
        if (turns < TURNS_NEEDED) {
            return "You wedge the axe between two spokes and lean on it. The wheel turns with a grinding "
                + "shriek, and the pipe knocks once inside the wall. (" + turns + "/" + TURNS_NEEDED + ")";
        }

        turns = 0;
        SaveState.cellarPuzzleSolved = true;
        SaveState.save();
        return "You wedge the axe into the spokes and turn the wheel the seventh time. Something lets go deep "
            + "in the pipe, and the wheel spins free on its own. Far above you, muffled by the floorboards, something "
            + "heavy falls over. It sounds like the sound came from the house.";
    }
}
