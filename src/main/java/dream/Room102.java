package dream;

import java.awt.Color;
import java.util.List;
import java.util.function.Supplier;

/** Room 102, the library. Its desk ends the run for a player looking for answers. */
final class Room102 {

    private static final List<Interactable> OBJECTS = List.of(
        Interactable.builder("key102")
            .names("iron key", "small key", "key")
            .look("A small iron key on the reading desk, its paper tag worn blank.")
            .take("You take the iron key.")
            .hiddenWhen(() -> Upstairs.isEnding(CyaLocation.ROOM_102))
            .build(),
        Interactable.builder("shelves")
            .names("shelves", "books", "bookcase", "bookshelf")
            .look("Shelves from floor to ceiling, every book on them the same thin grey volume.")
            .interact("You pull a book out. Behind it is another copy of the same book.")
            .refuseTake("The shelves are part of the wall.")
            .build(),
        Interactable.builder("desk")
            .names("desk", "reading desk", "black book", "book")
            .look(() -> Upstairs.isEnding(CyaLocation.ROOM_102)
                ? "A reading desk facing the window, a black book lying open on it. "
                    + "The drawers are all open except one."
                : "A reading desk facing the window, a chair pushed back from it. "
                    + "The drawers are all open except one.")
            .interact("You try the locked drawer. It does not move, and something inside it settles.")
            .endsSceneWhen(() -> Upstairs.isEnding(CyaLocation.ROOM_102), Room102::answersEnding)
            .refuseTake("The desk is heavier than it looks.")
            .build(),
        Interactable.builder("libraryWindow")
            .names("window", "glass")
            .look("The window looks out onto dark, and the dark looks levelly back.")
            .interact("You put a hand on the glass. It is warm.")
            .refuseTake("The window is part of the house.")
            .build(),
        Upstairs.exitDoor("door102Out")
    );

    private Room102() { }

    static List<Interactable> objects() {
        return OBJECTS;
    }

    static Scene.Exit play(Scene.Input input) throws InterruptedException {
        Supplier<String> description = () -> {
            String base = "The library is walled with shelves, every book on them the same thin "
                + "grey volume. A reading desk faces the window, a chair pushed back from it";
            if (Upstairs.isEnding(CyaLocation.ROOM_102)) {
                return base + ", and a black book lies open on it, the only thing in the room "
                    + "that is not grey.";
            }
            return SaveState.hasItem("key102")
                ? base + "."
                : base + ", and an iron key waits on the desk.";
        };
        return new Scene(CyaLocation.ROOM_102, "The Library", Scene.text(description), OBJECTS).play(input);
    }

    /** The answers ending: the one book that is not a copy, and the name it holds. */
    private static void answersEnding() throws InterruptedException {
        Terminal.print("You settle onto the desk, there's a book open to a chapter in an otherwise boring looking computer science text book", 60, Game.standardSize, Color.WHITE);
        Terminal.print("It spouts vaguely interesting points about artificial intelligence", 60, Game.standardSize, Color.WHITE);
        Terminal.print("There's a name on the desk of an Amélie Marin, how very... French", 60, Game.standardSize, Color.WHITE);
        Terminal.print("you read the books around the library, the books almost entirely consist of volumes of Amélie's family history and how the house was built.. absolutely riveting content about relaxed zoning laws and distant cousins, truly a page turner cover to cover.", 60, Game.standardSize, Color.WHITE);
        Terminal.print("Finally, the book you're looking for, a book about " + SaveState.userName + ", a familiar name", 60, Game.standardSize, Color.WHITE);
        Terminal.print("You open the book and look through the chapters", 60, Game.standardSize, Color.WHITE);
        Terminal.print("The pages are unintelligible, you can hardly make out a word", 60, Game.standardSize, Color.WHITE);
        

        Terminal.blankLine();
    }
}
