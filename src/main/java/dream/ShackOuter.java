package dream;

import java.util.List;

/** The path outside the decrepit shack. The first stop after the cemetary. */
final class ShackOuter {

    private static final List<Interactable> OBJECTS = List.of(
        Interactable.builder("shackDoor")
            .names("shack door", "door", "crooked door")
            .look("The door hangs open on one hinge. The wood is soft with rot, but it still moves when you push it.")
            .interact("You push the door. It swings inward with a sound like a held breath, and you step through.")
            .travelTo(() -> CyaLocation.SHACK_INNER)
            .refuseTake("You could take the door, but then the shack would be looking at you.")
            .build(),
        Interactable.builder("boards")
            .names("boards", "window", "boarded window")
            .look("The window is boarded shut from the inside. The nails are new.")
            .interact(ShackOuter::knockWindow)
            .refuseTake("The boards are nailed down. You would need a crowbar and a little more strength.")
            .build(),
        Interactable.builder("axe")
            .names("axe", "wood axe", "hatchet")
            .look("A remarkably nice Axe, the wood feels premium under your fingers and the metal head leaves nothing to be desired")
            .interact("You test the edge with your thumb. It would break something, given a reason.")
            .take("You take the axe. The weight of it feels good in your hands.")
            .useOn("hatch")
            .onlyWhile(() -> !SaveState.hatchBroken)
            .droppableWhen(() -> SaveState.hatchBroken)
            .hiddenWhen(() -> SaveState.isGone("axe"))
            .build(),
        Interactable.builder("rustyLantern") // lowkey might scrap lol
            .names("rusty lantern", "lantern")
            .look("A rusty lantern, still holding a little oil in the base.")
            .interact("The lantern is cold. The oil inside is still liquid.")
            .take("You take the rusty lantern. It is heavier than it looks.")
            .useOn("plateValve", "graveValve", "boardValve")
            .onlyWhile(() -> !SaveState.valvesOiled)
            .droppableWhen(() -> SaveState.valvesOiled)
            .hiddenWhen(() -> SaveState.isGone("rustyLantern"))
            .build()
    );

    private ShackOuter() { }

    static List<Interactable> objects() {
        return OBJECTS;
    }

    static Scene.Exit play(Scene.Input input) throws InterruptedException {
        return new Scene(CyaLocation.SHACK_OUTER, "The Outer Decrepit Shack",
            ShackOuter::describeArea, OBJECTS).play(input);
    }

    /** Built fresh each time, so the parts the player can act on are picked out. */
    private static List<TextSpan> describeArea() {
        String description = "The path ends at a shack that leans like it is trying to lie down. "
            + "Its windows are closed over with fresh boards, and a crooked door hangs open just "
            + "enough to let the dark out.";
        boolean axeHere = !SaveState.isGone("axe");
        boolean lanternHere = !SaveState.isGone("rustyLantern");
        if (axeHere && lanternHere) {
            description += " An axe leans against the wall beside the door, and a rusty lantern hangs "
                + "from a nail there.";
        } else if (axeHere) {
            description += " An axe leans against the wall beside the door.";
        } else if (lanternHere) {
            description += " A rusty lantern hangs from a nail beside the door.";
        }
        return TextSpan.highlighting(description, Scene.NAME, "boards", "door", "axe", "lantern");
    }

    /** The boards answer once, then fall silent; after the cellar, they stay silent. */
    private static String knockWindow() {
        if (SaveState.cellarPuzzleSolved) {
            return "You knock on the boards. Nothing answers. Whatever was knocking has gone quiet.";
        }
        return "You knock on the boards. Something knocks back, once, then twice, then three times, "
            + "and then stops.";
    }
}
