package dream;

import java.util.List;
import java.util.Locale;

/**
 * The landing at the top of the house's stairs.
 *
 * <p>Three numbered doors lead off it. Which room ends the run depends on the
 * purpose picked at the start of the story: peace ends in the bedroom, answers
 * in the library and vengeance in the computer room. That room's door is the
 * only one with two locks, and the other two rooms hold one key to it each.
 */
final class Upstairs {

    /** The three rooms on the landing, in door order. */
    private static final List<CyaLocation> ROOMS = List.of(
        CyaLocation.ROOM_101, CyaLocation.ROOM_102, CyaLocation.ROOM_103);

    private static final List<Interactable> OBJECTS = List.of(
        door(CyaLocation.ROOM_101),
        door(CyaLocation.ROOM_102),
        door(CyaLocation.ROOM_103),
        Interactable.builder("stairsDown")
            .names("stairs", "staircase", "way down")
            .look("The stairs drop back down to the hall, where the portrait hangs.")
            .interact("You go back down the stairs.")
            .travelTo(() -> CyaLocation.HOUSE)
            .refuseTake("The stairs are part of the house. Probably.")
            .build()
    );

    private Upstairs() { }

    static List<Interactable> objects() {
        return OBJECTS;
    }

    static CyaLocation play(Scene.Input input) throws InterruptedException {
        return new Scene(CyaLocation.UPSTAIRS, "The Upstairs", Scene.text(Upstairs::description), OBJECTS)
            .play(input);
    }

    /**
     * The room this run ends in, chosen by the purpose picked at the start.
     * A run without a purpose, such as a --scene checkpoint, ends in the
     * library.
     */
    static CyaLocation endingRoom() {
        if ("peace".equalsIgnoreCase(SaveState.purpose)) {
            return CyaLocation.ROOM_101;
        }
        if ("vengeance".equalsIgnoreCase(SaveState.purpose)) {
            return CyaLocation.ROOM_103;
        }
        return CyaLocation.ROOM_102;
    }

    /** True when this room is where the run's purpose is waiting. */
    static boolean isEnding(CyaLocation room) {
        return endingRoom() == room;
    }

    /** The id of the key that waits in one of the rooms. */
    static String keyId(CyaLocation room) {
        return switch (room) {
            case ROOM_101 -> "key101";
            case ROOM_102 -> "key102";
            case ROOM_103 -> "key103";
            default -> throw new IllegalArgumentException("no key in " + room);
        };
    }

    /** What that key is called, for the ending door's tags. */
    static String keyName(CyaLocation room) {
        return material(room) + " key";
    }

    private static String material(CyaLocation room) {
        return switch (room) {
            case ROOM_101 -> "brass";
            case ROOM_102 -> "iron";
            case ROOM_103 -> "silver";
            default -> "key";
        };
    }

    /** The two rooms that hold the keys to the ending room. */
    private static List<CyaLocation> keyRooms(CyaLocation ending) {
        return ROOMS.stream().filter(room -> room != ending).toList();
    }

    /** How many of the ending room's two keys the player is carrying. */
    private static int keysHeld(CyaLocation ending) {
        int held = 0;
        for (CyaLocation room : keyRooms(ending)) {
            if (SaveState.hasItem(keyId(room))) {
                held++;
            }
        }
        return held;
    }

    /** The landing, which says which door is still shut. */
    private static String description() {
        CyaLocation ending = endingRoom();
        return "The landing at the top of the stairs is narrow and warm, the carpet worn thin in "
            + "a line from door to door. Three doors face you, numbered 101, 102 and 103. Two of "
            + "them stand ajar; the one marked " + number(ending) + " is shut, with two locks "
            + "beside the handle.";
    }

    private static String number(CyaLocation room) {
        return switch (room) {
            case ROOM_101 -> "101";
            case ROOM_102 -> "102";
            case ROOM_103 -> "103";
            default -> throw new IllegalArgumentException("not an upstairs room: " + room);
        };
    }

    /** The room's name with its article, for prose. */
    private static String label(CyaLocation room) {
        return room.displayName.toLowerCase(Locale.ROOT);
    }

    /**
     * One of the landing's doors. The two that are not the ending room open at a
     * push; the ending room's door has two locks, one for each of the keys the
     * other rooms give up.
     */
    private static Interactable door(CyaLocation room) {
        String tag = number(room);
        String roomLabel = label(room);
        String shortName = roomLabel.startsWith("the ") ? roomLabel.substring(4) : roomLabel;
        return Interactable.builder("door" + tag)
            .names("door " + tag, tag, "room " + tag, shortName + " door", shortName)
            .look(() -> isEnding(room)
                ? "The only door on the landing with two locks. Small tags beside them read: "
                    + keyTags(room) + "."
                : "A door painted over so many times that its number is almost gone. "
                    + "A paper tag hangs from the handle: " + tag + ".")
            .interact(() -> isEnding(room)
                ? lockedLine(room)
                : "The door to " + roomLabel + " swings open.")
            .travelTo(() -> isEnding(room) && keysHeld(room) < 2 ? null : room)
            .refuseTake("The door is part of the house. It would take the wall with it.")
            .build();
    }

    /** The two key materials, for the tags on the ending door. */
    private static String keyTags(CyaLocation ending) {
        List<CyaLocation> rooms = keyRooms(ending);
        return material(rooms.get(0)) + " and " + material(rooms.get(1));
    }

    /** What the ending door says, depending on which of its keys are carried. */
    private static String lockedLine(CyaLocation ending) {
        int held = keysHeld(ending);
        if (held == 2) {
            return "Both keys turn at once, and the door to " + label(ending) + " swings open.";
        }
        String state = held == 0
            ? "Neither key is in your pocket."
            : "One key turns, but the second lock holds.";
        return "The door is locked with two locks. " + state
            + " The tags beside them read: " + keyTags(ending) + ".";
    }

    /** The way back out of one of the upstairs rooms, onto the landing. */
    static Interactable exitDoor(String id) {
        return Interactable.builder(id)
            .names("door", "way out", "landing")
            .look("The door back out onto the landing, and the stairs beyond it.")
            .interact("You step back out onto the landing.")
            .travelTo(() -> CyaLocation.UPSTAIRS)
            .refuseTake("The door is part of the house.")
            .build();
    }
}
