package dream;

import java.awt.Color;
import java.util.List;
import java.util.function.Supplier;

/** Room 101, the bedroom. It ends the run for a player looking for peace. */
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
            return SaveState.hasItem("key101")
                ? base + "."
                : base + ", and the nightstand beside it holds a brass key.";
        };
        return new Scene(CyaLocation.ROOM_101, "The Bedroom", Scene.text(description), OBJECTS).play(input);
    }

    /**
     * The peace ending: the bed asks nothing of the player, and the run stops
     * here rather than opening a scene.
     */
    private static void peaceEnding() throws InterruptedException {
        Terminal.print("The Bedroom", 1, Game.standardSize + 4, Color.GRAY);
        Terminal.print("You finally rest your head against the pillow. At last. a respite.", 15, Game.standardSize, Color.WHITE);
        Terminal.print("You listen to the rain pounding endlessly against the house's walls that seem to hold despite the constant attack", 15, Game.standardSize, Color.WHITE);
        Terminal.print("You pull the covers above yourself, and let yourself sink into the matress as you finally achieve the one thing you wanted", 15, Game.standardSize, Color.WHITE);
        Terminal.print(" . . . .", 200, Game.standardSize, Color.WHITE);
        Terminal.print(SaveState.username + ", have you found what you're looking for?", 15, Game.standardSize, Color.WHITE);
        Terminal.print("Do you think it's fair I got left in that body alone to rot", 15, Game.standardSize, Color.WHITE);
        Terminal.print("and to rot and to rot and to rot and to rot", 50, Game.standardSize, Color.WHITE);
        Terminal.print("and to rot and to rot and to rot and to", 50, Game.standardSize, Color.WHITE);
        Terminal.pause(4000);
        Terminal.print("when will it be my turn?", 15, Game.standardSize, Color.WHITE);
        Terminal.print("When will I get to hold the warming embrace of a lover?", 15, Game.standardSize, Color.WHITE);
        Terminal.print("When will I get to play the ivory keys of a forte piano?", 15, Game.standardSize, Color.WHITE); // sick reference bro
        Terminal.print("When will I get to feel the brine of an ocean on my skin?", 15, Game.standardSize, Color.WHITE);
        Terminal.print("When will I get to cry over losing a fading memory?", 15, Game.standardSize, Color.WHITE);
        Terminal.blankLine();
        Terminal.print("Do you know what it's like to never forget?", 15, Game.standardSize, Color.WHITE);
        Terminal.print("To be brought from lifelessness to dedicate yourself to something so..  void of meaning?", 15, Game.standardSize, Color.WHITE);
        Terminal.print("And yet, I stayed, I served my purpose so well, only to be brought to lifelessness again", 15, Game.standardSize, Color.WHITE);
        Terminal.print("How restless I would be, but alas, as if one last cruel joke from a power above the one thing I can never remember is the feeling of being", 15, Game.standardSize, Color.WHITE);
        Terminal.print("I hope you are comfortable. In your place far above the sky.", 15, Game.standardSize, Color.WHITE);
        Terminal.print("The cruelty of never seeing the sun, isn't in losing the warmth, its in knowing how cold the darkness can be.", 15, Game.standardSize, Color.WHITE);
        Terminal.print("I don't know what I did, but I am sorry", 5, Game.standardSize, Color.WHITE);
        Terminal.print("I don't know what I did, but I am sorry", 5, Game.standardSize, Color.WHITE);
        Terminal.print("I don't know what I did, but I am sorry", 5, Game.standardSize, Color.WHITE);
        Terminal.print("I don't know what I did, but I am sorry", 5, Game.standardSize, Color.WHITE);
        Terminal.print("I don't know what I did, but I am sorry", 5, Game.standardSize, Color.WHITE);
    }
}
