package dream;

import java.util.List;

/** The house waiting behind the shack's back door. */
final class House {

    private static final List<Interactable> OBJECTS = List.of(
        Interactable.builder("portrait")
            .names("portrait", "painting")
            .look("A portrait of someone in casual business attire, they surround a computer that you swear you've seen somewhere, they look very.. friendly")
            .interact("You look at the portrait. The paint is still wet.")
            .refuseTake("No matter how hard you pull the portrait seems to stick to the wall as if it were a part of it")
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
        String description = "The unlocked door opens into a house far too large to have fit behind the "
            + "shack. A long hall stretches ahead, lined with doors that are all slightly open. "
            + "Somewhere upstairs, a floorboard creaks.";
        return new Scene(CyaLocation.HOUSE, "The House", description, OBJECTS).play(input);
    }
}
