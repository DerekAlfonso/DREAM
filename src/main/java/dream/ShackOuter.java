package dream;

import java.util.List;

/** The path outside the decrepit shack. The first stop after the cemetary. */
final class ShackOuter {

    private static final List<Interactable> OBJECTS = List.of(
        Interactable.builder("shackDoor")
            .names("shack door", "door", "crooked door")
            .look("The door hangs open on one hinge. The wood is soft with rot, but it still moves when you push it.")
            .interact("You push the door. It swings inward with a sound like a held breath.")
            .refuseTake("You could take the door, but then the shack would be looking at you.")
            .build(),
        Interactable.builder("boards")
            .names("boards", "window", "boarded window")
            .look("The window is boarded shut from the inside. The nails are new.")
            .interact("You knock on the boards. Something knocks back, once, and then stops.")
            .refuseTake("The boards are nailed down. You would need a crowbar and a worse idea.")
            .build(),
        Interactable.builder("rustyLantern")
            .names("rusty lantern", "lantern")
            .look("A rusty lantern hangs from a nail by the door. There is still a little oil in it.")
            .interact("The lantern is cold. The oil inside is still liquid.")
            .take("You take the rusty lantern. It is heavier than it looks.")
            .build(),
        Interactable.builder("weatherVane")
            .names("weather vane", "weathervane", "vane")
            .look("A weather vane shaped like a hand points at the shack, no matter which way the wind blows.")
            .interact("You flick the weather vane. It turns to face you and stops.")
            .refuseTake("The weather vane is nailed to the roof. It points at you now.")
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
