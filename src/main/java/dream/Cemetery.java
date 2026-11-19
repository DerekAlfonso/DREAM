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
            .interact("You speak to the man. He turns his head slowly, as if he had been waiting for you.",
                () -> {
                    SaveState.spokenToMysteriousMan = true;
                    SaveState.save();
                })
            .take("You take the mysterious man. He does not resist. This raises several questions.")
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
        return new Scene(CyaLocation.CEMETARY, "The Cemetary", description, OBJECTS).play(input);
    }
}
