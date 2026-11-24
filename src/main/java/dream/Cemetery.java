package dream;

import java.util.List;

/**
 * The cemetary scene. Its objects live here; the command loop and inventory
 * are shared through {@link Scene}.
 */
final class Cemetery {

    private static final List<Interactable> OBJECTS = List.of(
        Interactable.builder("graves")
            .names("headstones", "graves", "gravestones", "stones")
            .look("Headstones lean in a crooked row, none of them with a name left on it. "
                + "Someone has kept them clear of the grass.")
            .interact("You rest a hand on the nearest headstone. It is warm, as if something had been "
                + "leaning against it.")
            .refuseTake("You can't take the graves. They are the only company the man has.")
            .build(),
        Interactable.builder("mysteriousMan")
            .names("mysterious man", "man", "silhouette", "stranger")
            .look("The vague silhouette of a man is stood motionless at the edge of the cemetary, you can't make out anything about him.")
            .interact("You speak to the man. He turns his head slowly, as if he had been waiting for"
                + " you. He presses a crumpled piece of paper into your hand. When you look up, he is gone."
                + " The crumpled note is in your inventory now.",
                () -> {
                    SaveState.spokenToMysteriousMan = true;
                    SaveState.addItem("crumpledPaper");
                })
            .refuseTake("You can't take the man. That's kidnapping. Which is bad.")
            .hiddenWhen(() -> SaveState.spokenToMysteriousMan)
            .build(),
        // The man's parting gift. It only shows up in the scene once he has
        // handed it over, and it is in the inventory from that moment on, so it
        // is never really listed here: this entry is what names it in INVENTORY.
        Interactable.builder("crumpledPaper")
            .names("crumpled piece of paper", "paper", "piece of paper", "note")
            .look(TextSpan.glitching(
                "A crumpled piece of paper, the words are smudged and worn.. how long was he out here for?"
                    + " No matter how hard you squint the only words you can make out are \"Did you feel trapped in that rotting body, old friend?\" and something about a shack... weird."
                    + " Underneath, pressed hard enough to tear the paper: \"three stops under the floor: the plate, the boards, the grave. he took the plate before the boards, and the grave was never first.\"",
                "rotting body"))
            .hiddenWhen(() -> !SaveState.spokenToMysteriousMan)
            .build()
    );

    private Cemetery() { }

    static List<Interactable> objects() {
        return OBJECTS;
    }

    static CyaLocation play(Scene.Input input) throws InterruptedException {
        return new Scene(CyaLocation.CEMETARY, "The Cemetary", Cemetery::describeArea, OBJECTS)
            .play(input);
    }

    /** Built fresh each time, so a LOOK after the man leaves no longer mentions him. */
    private static List<TextSpan> describeArea() {
        String description = "You walk into a cemetary, it smells like wet dirt and feels like empty promises."
            + " A row of headstones leans in the grass near the gate.";
        if (!SaveState.spokenToMysteriousMan) {
            description += " The vague silhouette of a man is stood motionless at the edge of the cemetary, you can't make out anything about him.";
        }
        return TextSpan.highlighting(description, Scene.NAME, "headstones", "man");
    }
}
