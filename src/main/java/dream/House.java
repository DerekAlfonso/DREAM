package dream;

import java.util.List;

/** The house waiting behind the shack's back door. */
final class House {

    private static final List<Interactable> OBJECTS = List.of(
        Interactable.builder("portrait")
            .names("portrait", "painting")
            .look("A portrait of a man in a dark coat. His face has been painted over, but the shape of his shoulders is familiar.")
            .interact("You look at the portrait. The paint is still wet.")
            .refuseTake("The portrait is part of the wall. Or the wall is part of the portrait.")
            .build(),
        Interactable.builder("candlestick")
            .names("candlestick", "candle")
            .look("A brass candlestick, its candle burned down to a thumb.")
            .take("You take the candlestick. The flame leans toward the stairs.")
            .build(),
        Interactable.builder("stairs")
            .names("stairs", "staircase")
            .look("A staircase climbs into a darkness that does not match the ceiling.")
            .interact("You put a foot on the first step. The house creaks somewhere above you.")
            .refuseTake("The stairs are part of the house. Probably.")
            .build(),
        Interactable.builder("coatRack")
            .names("coat rack", "coatrack", "coat")
            .look("A coat rack by the door, holding a coat that is still dripping.")
            .interact("You lift the coat. Underneath is a hat, and under that is nothing at all.")
            .refuseTake("The coat is wet through. You would rather not.")
            .build()
    );

    private House() { }

    static List<Interactable> objects() {
        return OBJECTS;
    }

    static CyaLocation play(Scene.Input input) throws InterruptedException {
        String description = "The back door opens into a house that is far too large to have fit inside "
            + "the shack. A long hall stretches ahead, lined with doors that are all slightly open. "
            + "Somewhere upstairs, a floorboard creaks.";
        return new Scene(CyaLocation.HOUSE, "The House", description, OBJECTS).play(input);
    }
}
