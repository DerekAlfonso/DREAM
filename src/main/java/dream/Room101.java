package dream;

import java.awt.Color;
import java.util.List;
import java.util.function.Supplier;

/** Room 101, the bedroom. Its bed ends the run for a player looking for peace. */
final class Room101 {

    private static final List<Interactable> OBJECTS = List.of(
        Interactable.builder("key101")
            .names("brass key", "small key", "key")
            .look("A small brass key on the nightstand, its paper tag worn blank.")
            .take("You take the brass key.")
            .hiddenWhen(() -> Upstairs.isEnding(CyaLocation.ROOM_101))
            .build(),
        Interactable.builder("bed")
            .names("bed", "narrow bed")
            .look("A narrow bed, made so tightly that it has the look of something never slept in.")
            .interact("You press a hand into the blanket. It is cold, and perfectly flat.")
            .endsSceneWhen(() -> Upstairs.isEnding(CyaLocation.ROOM_101), Room101::peaceEnding)
            .refuseTake("The bed is heavier than it looks.")
            .build(),
        Interactable.builder("nightstand")
            .names("nightstand", "bedside table", "drawer")
            .look("A small nightstand beside the bed, its drawer open a finger's width.")
            .interact("You pull the drawer open. It is empty, and the dust inside has been disturbed.")
            .refuseTake("The nightstand is part of the room.")
            .build(),
        Upstairs.exitDoor("door101Out")
    );

    private Room101() { }

    static List<Interactable> objects() {
        return OBJECTS;
    }

    static CyaLocation play(Scene.Input input) throws InterruptedException {
        Supplier<String> description = () -> {
            String base = "The bedroom is small and neat, the sort of room kept ready for a guest "
                + "who never arrives. A bed stands against the wall";
            if (Upstairs.isEnding(CyaLocation.ROOM_101)) {
                return base + ". The room is quiet, and for once nothing in it asks anything "
                    + "of you.";
            }
            return SaveState.hasItem("key101")
                ? base + "."
                : base + ", and the nightstand beside it holds a brass key.";
        };
        return new Scene(CyaLocation.ROOM_101, "The Bedroom", Scene.text(description), OBJECTS).play(input);
    }

    /** The peace ending: the bed asks nothing of the player, and the run stops. */
    private static void peaceEnding() throws InterruptedException {
        Terminal.print("you sit on the edge of the bed, then lie back. the ceiling does not "
            + "creak. somewhere below, the house settles, board by board, as if it has been "
            + "holding its breath.", 15, Game.standardSize, Color.WHITE);
        Terminal.print("you were looking for peace. it was at the top of the stairs the whole "
            + "time, made up and waiting.", 20, Game.standardSize, Color.GREEN);
        Terminal.blankLine();
    }
}
