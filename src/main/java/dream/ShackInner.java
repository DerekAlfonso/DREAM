package dream;

import java.util.List;

/**
 * The single room inside the decrepit shack. Its back door is the only way back
 * out to the yard, and the hatch in the floor leads down to the cellar once it
 * has been broken open.
 */
final class ShackInner {

    private static final List<Interactable> OBJECTS = List.of(
        Interactable.builder("table")
            .names("table", "wooden table")
            .look("A wooden table, set for one. The plate is clean.")
            .interact("You touch the table. It is warm.")
            .refuseTake("The table is too big for your pockets, and it is warm.")
            .build(),
        Interactable.builder("chair")
            .names("chair")
            .look("A chair pulled out from the table, as if someone has just stood up.")
            .interact("You touch the chair. It slides back an inch.")
            .refuseTake("You could drag the chair, but it feels like it would follow you.")
            .build(),
        Interactable.builder("stove")
            .names("stove", "cold stove", "cast-iron stove")
            .look("A cast-iron stove, cold to the touch. The firebox is full of ash and something that is not ash.")
            .interact("You open the stove. Something inside shifts.")
            .refuseTake("The stove is bolted to the floor.")
            .build(),
        Interactable.builder("hatch")
            .names("hatch", "floor hatch", "cellar hatch", "trapdoor")
            .look(ShackInner::describeHatch)
            .interact(ShackInner::useHatch)
            .travelTo(ShackInner::hatchDestination)
            .refuseTake("The hatch is part of the floor, and the floor is not going anywhere.")
            .build(),
        Interactable.builder("Door")
            .names("back door", "door")
            .look("The door is newer than the rest of the shack. Cold air leaks under it.")
            .interact("You pull the door open and step back out into the yard.")
            .travelTo(() -> CyaLocation.SHACK_OUTER)
            .refuseTake("You leave the door where it is.")
            .build(),
        Interactable.builder("rustyKey")
            .names("rusty key", "key")
            .look("A rusty key hangs from a nail beside the back door.")
            .take("You take the rusty key. It is cold, and it fits nothing here.")
            .build()
    );

    private ShackInner() { }

    static List<Interactable> objects() {
        return OBJECTS;
    }

    static CyaLocation play(Scene.Input input) throws InterruptedException {
        return new Scene(CyaLocation.SHACK_INNER, "The Inner Decrepit Shack",
            ShackInner::describeArea, OBJECTS).play(input);
    }

    /** Built fresh each time, so the hatch reads as open once it has been broken. */
    private static List<TextSpan> describeArea() {
        String description = "Inside, the shack is one long room that smells of wet wood and old smoke. "
            + "A table, a chair and a cold stove wait in the dark. There is another door at the back. ";
        description += SaveState.hatchBroken
            ? "The hatch in the floorboards hangs open, and a ladder drops through it into the dark."
            : "A hatch is set into the floorboards beside the stove, nailed shut.";
        return TextSpan.highlighting(description, "hatch", Scene.NAME);
    }

    private static String describeHatch() {
        if (SaveState.hatchBroken) {
            return "The hatch hangs open, a black square cut into the floorboards. A ladder drops away "
                + "into the dark below.";
        }
        return "A hatch is set into the floorboards, its boards crossed with long iron nails and its handle long "
            + "gone. It will not lift. It would have to be broken open.";
    }

    /** Breaks the hatch open if the player is carrying the axe, and says what happened. */
    private static String useHatch() {
        if (SaveState.hatchBroken) {
            return "You climb down the ladder and into the dark below.";
        }
        if (!SaveState.hasItem("axe")) {
            return "You get your fingers under the edge of the hatch and pull until they ache. The nails hold "
                + "fast and the boards do not shift. It would have to be broken open, and you have nothing here to "
                + "break it with.";
        }
        SaveState.hatchBroken = true;
        SaveState.save();
        return "You jam the axe under the boards and put your weight on the handle. The boards split, the "
            + "nails shriek out one by one, and the hatch drops open. A ladder leads down into the dark. You climb down.";
    }

    /** Follows the interaction: an open hatch is a way down, a shut one is not. */
    private static CyaLocation hatchDestination() {
        return SaveState.hatchBroken ? CyaLocation.CELLAR : null;
    }
}
