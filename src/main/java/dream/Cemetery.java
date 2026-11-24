package dream;

import java.util.List;

/**
 * The cemetary scene. Its objects live here; the command loop and inventory
 * are shared through {@link Scene}.
 */
final class Cemetery {

    private static final List<Interactable> OBJECTS = List.of(
        Interactable.builder("mysteriousMan")
            .names("mysterious man", "man", "silhouette", "stranger")
            .look("The vague silhouette of a man is stood motionless at the edge of the cemetary, you can't make out anything about him.")
            .interact("You speak to the man. He turns his head slowly, as if he had been waiting for"
                + " you. He presses a crumpled piece of paper into your hand. When you look up, he is gone.",
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
                    + "No matter how hard you squint the only words you can make out are \"Did you feel trapped in that rotting body, old friend?\" and something about a shack... weird.",
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
        String description = "You walk into a cemetary, it smells like wet dirt and feels like empty promises.";
        if (!SaveState.spokenToMysteriousMan && !SaveState.hasItem("mysteriousMan")) {
            description += " The vague silhouette of a man is stood motionless at the edge of the cemetary, you can't make out anything about him.";
        }
        return TextSpan.highlighting(description, "man", Scene.NAME);
    }
}
