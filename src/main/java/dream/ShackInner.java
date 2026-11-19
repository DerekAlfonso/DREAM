package dream;

import java.util.List;

/** The single room inside the decrepit shack. */
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
        Interactable.builder("backDoor")
            .names("back door", "door")
            .look("The back door is newer than the rest of the shack. Light leaks under it.")
            .interact("You try the handle. It opens.")
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
        String description = "Inside, the shack is one long room that smells of wet wood and old smoke. "
            + "A table, a chair and a cold stove wait in the dark. There is another door at the back.";
        return new Scene(CyaLocation.SHACK_INNER, "The Inner Decrepit Shack", description, OBJECTS)
            .play(input);
    }
}
