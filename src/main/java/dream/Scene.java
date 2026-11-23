package dream;

import java.awt.Color;
import java.util.List;
import java.util.function.Supplier;

/**
 * A CYA location: a title, an opening description and the objects in it.
 *
 * <p>The command loop, inventory and GO travel live here, so each area file
 * only has to declare its text and its object list.
 */
final class Scene {

    /** Colour that marks an object's name, so the thing you can type stands out. */
    static final Color NAME = Color.GREEN;

    @FunctionalInterface
    interface Input {
        String read() throws InterruptedException;
    }

    private final CyaLocation here;
    private final String title;
    private final Supplier<List<TextSpan>> description;
    private final List<Interactable> objects;

    Scene(CyaLocation here, String title, String description, List<Interactable> objects) {
        this(here, title, () -> List.of(TextSpan.of(description)), objects);
    }

    /**
     * Same, for a description that changes with the story: it is built on each
     * print, so a bare LOOK never repeats text that no longer applies.
     */
    Scene(CyaLocation here, String title, Supplier<List<TextSpan>> description,
            List<Interactable> objects) {
        this.here = here;
        this.title = title;
        this.description = description;
        this.objects = List.copyOf(objects);
    }

    /**
     * Plays the area until the player travels or leaves.
     *
     * @return the area chosen with GO, or null if the player stopped exploring
     */
    CyaLocation play(Input input) throws InterruptedException {
        Terminal.print(title, 1, Game.standardSize + 4, Color.GRAY);
        Terminal.print(description.get(), 15, Game.standardSize, Color.WHITE);
        Terminal.print("type HELP for all commands, or LOOK to look around.",
            15, Game.standardSize, Color.GREEN);

        while (true) {
            Terminal.print("what do you do?", 15, Game.standardSize, Color.WHITE);
            String line = input.read().trim();
            String[] parts = line.split("\\s+", 2);
            String verb = parts[0].toLowerCase();
            String target = parts.length > 1 ? parts[1].trim() : "";

            if (verb.equals("leave") || Game.isBackCommand(line)) {
                Terminal.blankLine();
                return null;
            }

            switch (verb) {
                case "look":
                    if (target.toLowerCase().startsWith("at ")) {
                        target = target.substring(3).trim();
                    }
                    if (target.isEmpty()) {
                        describeArea();
                    } else {
                        look(target);
                    }
                    break;
                case "interact":
                case "talk":
                    interact(target);
                    break;
                case "take":
                    take(target);
                    break;
                case "go": {
                    CyaLocation destination = go(target);
                    if (destination != null) {
                        return destination;
                    }
                    break;
                }
                case "inventory":
                    showInventory();
                    break;
                case "help":
                    printHelp();
                    break;
                default:
                    Terminal.print("unknown command. type HELP for all commands.",
                        15, Game.standardSize, Color.RED);
                    break;
            }
        }
    }

    /** Bare LOOK: repeat where the player is, then what is here to see. */
    private void describeArea() {
        Terminal.print(description.get(), 15, Game.standardSize, Color.WHITE);
        listObjects();
    }

    private void listObjects() {
        boolean any = false;
        for (Interactable object : objects) {
            if (object.isHidden() || SaveState.hasItem(object.id)) {
                continue;
            }
            Terminal.print(TextSpan.highlighting("- " + object.displayName,
                object.displayName, NAME), 15, Game.standardSize, Color.WHITE);
            any = true;
        }
        if (!any) {
            Terminal.print("there is nothing else here.", 15, Game.standardSize, Color.GRAY);
        }
    }

    private void look(String target) {
        Interactable object = find(target);
        if (object == null) {
            Terminal.print("you don't see that here.", 15, Game.standardSize, Color.RED);
            return;
        }
        if (SaveState.hasItem(object.id)) {
            Terminal.print(TextSpan.highlighting("you are carrying the "
                + object.displayName + ".", object.displayName, NAME),
                15, Game.standardSize, Color.WHITE);
        }
        // A carried item still describes itself, so its text is not lost once it
        // leaves the scene and only the inventory can reach it.
        Terminal.print(TextSpan.highlighting(object.lookText,
            object.displayName, NAME), 15, Game.standardSize, Color.WHITE);
    }

    private void interact(String target) {
        if (target.isEmpty()) {
            printTargetUsage("interact", null);
            return;
        }
        Interactable object = find(target);
        if (object == null) {
            printTargetUsage("interact", target);
            return;
        }
        if (object.onInteract != null) {
            object.onInteract.run();
        }
        Terminal.print(TextSpan.highlighting(object.interactText,
            object.displayName, NAME), 15, Game.standardSize, Color.WHITE);
    }

    private void take(String target) {
        if (target.isEmpty()) {
            printTargetUsage("take", null);
            return;
        }
        Interactable object = find(target);
        if (object == null) {
            printTargetUsage("take", target);
            return;
        }
        if (SaveState.hasItem(object.id)) {
            Terminal.print(TextSpan.highlighting("you already have the "
                + object.displayName + ".", object.displayName, NAME),
                15, Game.standardSize, Color.WHITE);
            return;
        }
        Terminal.print(TextSpan.highlighting(object.takeText,
            object.displayName, NAME), 15, Game.standardSize, Color.WHITE);
        if (!object.takeable) {
            return;
        }
        SaveState.addItem(object.id);
        if (object.onTake != null) {
            object.onTake.run();
        }
    }

    /** Validates a GO target against this area's links. Null means stay put. */
    private CyaLocation go(String name) {
        String target = name.trim();
        if (target.toLowerCase().startsWith("to ")) {
            target = target.substring(3).trim();
        }
        if (target.isEmpty()) {
            listExits();
            return null;
        }

        CyaLocation destination = CyaLocation.fromName(target);
        if (destination == null) {
            Terminal.print("there is no area called \"" + target + "\". use GO to see where you can go.",
                15, Game.standardSize, Color.RED);
            return null;
        }
        if (destination == here) {
            Terminal.print("you are already there.", 15, Game.standardSize, Color.RED);
            return null;
        }
        if (!CyaLocation.neighbours(here).contains(destination)) {
            Terminal.print("you can't get to " + destination.displayName
                + " from here. use GO to see where you can go.",
                15, Game.standardSize, Color.RED);
            return null;
        }

        Terminal.blankLine();
        return destination;
    }

    private void listExits() {
        List<CyaLocation> exits = CyaLocation.neighbours(here);
        if (exits.isEmpty()) {
            Terminal.print("there is nowhere else to go.", 15, Game.standardSize, Color.GRAY);
            return;
        }
        Terminal.print("you can go to:", 15, Game.standardSize, Color.WHITE);
        for (CyaLocation exit : exits) {
            Terminal.print("- " + exit.displayName, 15, Game.standardSize, Color.WHITE);
        }
    }

    private void showInventory() {
        if (SaveState.takenObjects.isEmpty()) {
            Terminal.print("your inventory is empty.", 15, Game.standardSize, Color.GRAY);
            return;
        }
        for (String id : SaveState.takenObjects) {
            Interactable object = ObjectCatalog.byId(id);
            String name = object == null ? id : object.displayName;
            Terminal.print(TextSpan.highlighting("- " + name, name, NAME),
                15, Game.standardSize, Color.WHITE);
        }
    }

    private void printHelp() {
        Terminal.print("COMMANDS -----------", 15, Game.standardSize, Color.CYAN);
        printCommand("look", "describe the area and list what is here");
        printCommand("look [at] <thing>", "examine something");
        printCommand("interact <thing>", "talk to or use something");
        printCommand("talk <thing>", "alias for interact <thing>");
        printCommand("take <thing>", "pick something up");
        printCommand("inventory", "show what you are carrying");
        printCommand("go", "list where you can go");
        printCommand("go [to] <area>", "travel to a connected area");
        printCommand("help", "show this command list");
        printCommand("leave", "finish exploring");
        printCommand("back | return | home", "aliases for leave");
        printCommand("quit | exit", "quit the game (with confirmation)");
        Terminal.print("------------------", 15, Game.standardSize, Color.GREEN);
    }

    /**
     * Column the arrows start in: the longest syntax ("back | return | home")
     * plus a gap, so the arrow never touches the command or its description.
     */
    private static final int SYNTAX_WIDTH = 22;

    private static void printCommand(String syntax, String description) {
        StringBuilder line = new StringBuilder(syntax);
        while (line.length() < SYNTAX_WIDTH) {
            line.append(' ');
        }
        line.append("--> ").append(description);
        Terminal.print(line.toString(), 1, Game.standardSize, Color.GREEN);
    }

    private void printTargetUsage(String verb, String target) {
        String command = verb.toUpperCase();
        String message = target == null
            ? verb + " what? use " + command + " <thing>, or LOOK to see what is here."
            : "you don't see \"" + target + "\" here. use " + command
                + " <thing>, or LOOK to see what is here.";
        Terminal.print(message, 15, Game.standardSize, Color.RED);
    }

    private Interactable find(String name) {
        String target = name.trim();
        if (target.toLowerCase().startsWith("the ")) {
            target = target.substring(4).trim();
        }
        for (Interactable object : objects) {
            if (!object.isHidden() && object.matches(target)) {
                return object;
            }
        }
        return null;
    }
}
