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
            .interact("You knock on the boards. Something knocks back, once, and then stops.")
            .refuseTake("The boards are nailed down. You would need a crowbar and a little more strength.")
            .build(),
        Interactable.builder("axe")
            .names("axe", "wood axe", "hatchet")
            .look("A remarkably nice Axe, the wood feels premium under your fingers and the metal head leaves nothing to be desired")
            .interact("You test the edge with your thumb. It would break something, given a reason.")
            .take("You take the axe. The weight of it makes the shack feel smaller.")
            .build(),
        Interactable.builder("rustyLantern")
            .names("rusty lantern", "lantern")
            .look("A rusty lantern hangs from a nail by the door. There is still a little oil in it.")
            .interact("The lantern is cold. The oil inside is still liquid.")
            .take("You take the rusty lantern. It is heavier than it looks.")
            .build()
    );

    private ShackOuter() { }

    static List<Interactable> objects() {
        return OBJECTS;
    }

    static CyaLocation play(Scene.Input input) throws InterruptedException {
        String description = "The path ends at a shack that leans like it is trying to lie down. "
            + "Its windows are boarded from the inside, and a crooked door hangs open just enough "
            + "to let the dark out.";
        return new Scene(CyaLocation.SHACK_OUTER, "The Outer Decrepit Shack", description, OBJECTS)
            .play(input);
    }
}
