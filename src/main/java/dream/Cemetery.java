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
            .look("A crumpled piece of paper, smoothed out just enough to read. The creases are"
                + " deep and the only words you can make out are your own name.")
            .hiddenWhen(() -> !SaveState.spokenToMysteriousMan)
            .build()
    );

    private Cemetery() { }

    static List<Interactable> objects() {
        return OBJECTS;
    }

    static CyaLocation play(Scene.Input input) throws InterruptedException {
        String description = "You walk into a cemetary, it smells like wet dirt and feels like empty promises.";
        if (!SaveState.spokenToMysteriousMan && !SaveState.hasItem("mysteriousMan")) {
            description += " The vague silhouette of a man is stood motionless at the edge of the cemetary, you can't make out anything about him.";
        }
        return new Scene(CyaLocation.CEMETARY, "The Cemetary",
            TextSpan.highlighting(description, "man", Scene.NAME), OBJECTS).play(input);
    }
}
