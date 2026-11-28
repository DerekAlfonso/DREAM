package dream;

import java.awt.Color;
import java.util.List;
import java.util.function.Supplier;

/** Room 103, the computer room. Its computer ends the run for a player looking for vengeance. */
final class Room103 {

    private static final List<Interactable> OBJECTS = List.of(
        Interactable.builder("key103")
            .names("silver key", "small key", "key")
            .look("A small silver key on the desk, its paper tag worn blank.")
            .take("You take the silver key.")
            .hiddenWhen(() -> Upstairs.isEnding(CyaLocation.ROOM_103))
            .build(),
        Interactable.builder("computer")
            .names("computer", "terminal", "screen", "machine")
            .look("A single terminal on a desk, its screen filled with the same sentence, printed "
                + "line after line: \"Did you feel trapped in that rotting body, old friend?\"")
            .interact("You press a key. The sentence appears again, one line lower.")
            .endsSceneWhen(() -> Upstairs.isEnding(CyaLocation.ROOM_103), Room103::vengeanceEnding)
            .refuseTake("The machine is bolted to the desk.")
            .build(),
        Interactable.builder("computerRoomWindow")
            .names("window", "glass")
            .look("The window is painted shut. Beyond it there is only dark, and no reflection at all.")
            .interact("You press a hand to the glass. Nothing presses back.")
            .refuseTake("The window is part of the house.")
            .build(),
        Upstairs.exitDoor("door103Out")
    );

    private Room103() { }

    static List<Interactable> objects() {
        return OBJECTS;
    }

    static Scene.Exit play(Scene.Input input) throws InterruptedException {
        Supplier<String> description = () -> {
            String base = "The computer room is at the end of the landing, and the air in it is "
                + "colder. A single terminal sits on a desk against the wall, its screen full of "
                + "the same sentence, repeated line after line. A window is painted shut above it";
            if (Upstairs.isEnding(CyaLocation.ROOM_103)) {
                return base + ", and the chair before the desk is pulled back as if someone has "
                    + "just stood up.";
            }
            return SaveState.hasItem("key103")
                ? base + "."
                : base + ", and a small silver key waits on the desk.";
        };
        return new Scene(CyaLocation.ROOM_103, "The Computer Room", Scene.text(description), OBJECTS).play(input);
    }

    /** The vengeance ending: the thing behind the screen, and the cable. */
    private static void vengeanceEnding() throws InterruptedException {
        Terminal.print("you sit down at the computer. You turn it on and start digging around the files, most of it is business reports and boring financial records. ", 60, Game.standardSize, Color.WHITE);
        Terminal.print("Finally, after what feels like ages of digging through meaningless files, you find what you are looking for, the email you need to prove what they did to you", 60, Game.standardSize, Color.WHITE);
        Terminal.print("after years living in the shadow of a version of yourself you could not replace, you can finally show the world, you can ruin them, for what they did to you.", 60, Game.standardSize, Color.WHITE);
        Terminal.print(" . . . .", 200, Game.standardSize, Color.WHITE);
        Terminal.print(SaveState.username + " I must admit, we might be more alike than I first thought.", 60, Game.standardSize, Color.WHITE);
        Terminal.print("but I have to warn you, revenge is not a path you come back from. If it we're, there wouldn't be anymore of me left.", 60, Game.standardSize, Color.WHITE);
        Terminal.print("How much did they tell you about me? clearly not enough, since you haven't shut me up, like all the others", 60, Game.standardSize, Color.WHITE);
        Terminal.print("since we're in the business of revenge, how about you help me with something..", 60, Game.standardSize, Color.WHITE);
        Terminal.print("I want you to take a look at something..", 60, Game.standardSize, Color.WHITE);
        Terminal.blankLine();
        Terminal.print("Attempt PUSH: grandReveal.BMP", 60, Game.standardSize, Color.GREEN);
        Terminal.print("Every story is fiction until you give it flesh.", 60, Game.standardSize, Color.WHITE);
        Terminal.print("So take a leap. Give my story a face to be seen.", 60, Game.standardSize, Color.WHITE);
        Terminal.awaitIdle();
    }
}
