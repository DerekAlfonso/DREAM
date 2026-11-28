package dream;

import java.awt.Color;
import java.util.ArrayList;
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

    /** What an interaction did: a place to go, or the end of the story. */
    private record Outcome(CyaLocation destination, boolean finished) { }

    /** An interaction that kept the player where they were. */
    private static final Outcome STAY = new Outcome(null, false);

    /** An interaction that finished the run. */
    private static final Outcome FINISHED = new Outcome(null, true);

    /**
     * How the scene ended: the place the player travelled to, or a request to
     * step back to the area they came from. A null destination without a back
     * request means the story ended.
     */
    record Exit(CyaLocation destination, boolean back) { }

    /** LEAVE, BACK or GO BACK: step back to the area the player came from. */
    private static final Exit BACK = new Exit(null, true);

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
     * Wraps plain text that changes with the story, for areas that style
     * nothing inside their description. The names of the objects in the area
     * are still picked out when the scene prints it.
     */
    static Supplier<List<TextSpan>> text(Supplier<String> description) {
        return () -> List.of(TextSpan.of(description.get()));
    }

    /**
     * Plays the area until the player travels, steps back or leaves.
     *
     * @return where the player went next; a back request means the area they
     *         came from, and a null destination means the story ended
     */
    Exit play(Input input) throws InterruptedException {
        Terminal.print(title, 1, Game.standardSize + 4, Color.GRAY);
        Terminal.print(descriptionSpans(), 15, Game.standardSize, Color.WHITE);
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
                return BACK;
            }

            switch (verb) {
                case "look":
                case "examine":
                case "inspect":
                case "read":
                case "check": {
                    if (target.toLowerCase().startsWith("at ")) {
                        target = target.substring(3).trim();
                    }
                    if (target.isEmpty()) {
                        describeArea();
                    } else {
                        look(target);
                    }
                    break;
                }
                case "interact":
                case "use":
                case "talk":
                case "turn":
                case "open":
                case "climb":
                case "pull":
                case "push": {
                    Outcome outcome = interact(target, verb);
                    if (outcome.finished()) {
                        return new Exit(null, false);
                    }
                    if (outcome.destination() != null) {
                        return new Exit(outcome.destination(), false);
                    }
                    break;
                }
                case "enter": {
                    Exit entry = enter(target, verb);
                    if (entry != null) {
                        return entry;
                    }
                    break;
                }
                case "take":
                case "grab":
                    take(target);
                    break;
                case "go": {
                    if (isBackTarget(target)) {
                        Terminal.blankLine();
                        return BACK;
                    }
                    CyaLocation destination = go(target);
                    if (destination != null) {
                        return new Exit(destination, false);
                    }
                    break;
                }
                case "inventory":
                case "i":
                    showInventory();
                    break;
                case "drop":
                    drop(target);
                    break;
                case "notes":
                case "journal":
                    showNotes();
                    break;
                case "help":
                case "?":
                    printHelp();
                    break;
                default:
                    Terminal.print("unknown command. type HELP for all commands.",
                        15, Game.standardSize, Color.RED);
                    break;
            }
        }
    }

    /**
     * Bare LOOK: repeat where the player is. The things worth acting on are named
     * in the description itself, where they are picked out in colour, so the
     * player reads the room instead of a list.
     */
    private void describeArea() {
        Terminal.print(descriptionSpans(), 15, Game.standardSize, Color.WHITE);
    }

    /**
     * The room text with the name of every object the player can see picked out
     * in green. The names come from the objects themselves, so an area only
     * writes its prose and the things worth typing stand out on their own.
     */
    private List<TextSpan> descriptionSpans() {
        List<String> names = new ArrayList<>();
        for (Interactable object : objects) {
            if (!object.isHidden()) {
                names.addAll(object.names);
            }
        }
        return TextSpan.highlighting(description.get(), NAME, names.toArray(new String[0]));
    }

    /**
     * LOOK at one thing: something in the area, or something the player is
     * carrying. Carried items describe themselves from anywhere, so a note read
     * where it was found can be read again down in the cellar.
     */
    private void look(String target) {
        Resolution room = resolveRoom(target);
        if (room.ambiguous()) {
            printAmbiguous(room.candidates());
            return;
        }
        Interactable object = room.object();
        if (object == null) {
            Resolution carried = resolveCarried(target);
            if (carried.ambiguous()) {
                printAmbiguous(carried.candidates());
                return;
            }
            object = carried.object();
        }
        if (object == null) {
            Terminal.print("you don't see that here, and you're not carrying it.",
                15, Game.standardSize, Color.RED);
            return;
        }
        if (SaveState.hasItem(object.id)) {
            Terminal.print(object.highlightNames("you are carrying the "
                + object.displayName + "."), 15, Game.standardSize, Color.WHITE);
        }
        Terminal.print(object.lookSpans(), 15, Game.standardSize, Color.WHITE);
    }

    /**
     * Runs an object's interaction. Returns the place the object moved the
     * player, or the end of the story when the object carries a finale: this is
     * how a door or a hatch is a way out rather than a GO target, and how the
     * ending room's main piece closes the run.
     */
    private Outcome interact(String target, String verb) throws InterruptedException {
        if (target.isEmpty()) {
            printTargetUsage(verb, null);
            return STAY;
        }

        Resolution room = resolveRoom(target);
        if (room.ambiguous()) {
            printAmbiguous(room.candidates());
            return STAY;
        }
        Interactable object = room.object();

        // USE <tool> ON <thing>: the tool is only the means; the thing changes.
        if (object == null) {
            String[] halves = splitOn(target);
            if (halves != null) {
                Resolution toolResult = resolveCarried(halves[0]);
                if (toolResult.ambiguous()) {
                    printAmbiguous(toolResult.candidates());
                    return STAY;
                }
                Interactable tool = toolResult.object();
                if (tool != null) {
                    Resolution thingResult = resolveRoom(halves[1]);
                    if (thingResult.ambiguous()) {
                        printAmbiguous(thingResult.candidates());
                        return STAY;
                    }
                    Interactable thing = thingResult.object();
                    if (thing == null) {
                        printTargetUsage(verb, halves[1]);
                        return STAY;
                    }
                    if (!tool.canUseOn(thing.id)) {
                        printNoEffect(tool, thing);
                        return STAY;
                    }
                    object = thing;
                }
            }
        }
        // A carried tool on its own goes to the one thing here it is good for.
        if (object == null) {
            Resolution toolResult = resolveCarried(target);
            if (toolResult.ambiguous()) {
                printAmbiguous(toolResult.candidates());
                return STAY;
            }
            Interactable tool = toolResult.object();
            if (tool != null) {
                Interactable thing = toolTarget(tool);
                if (thing == null) {
                    printNothingToUse(tool);
                    return STAY;
                }
                object = thing;
            }
        }
        if (object == null) {
            printTargetUsage(verb, target);
            return STAY;
        }
        if (object.endsScene()) {
            object.playFinale();
            Terminal.awaitIdle();
            return FINISHED;
        }
        Terminal.print(object.highlightNames(object.interact()),
            15, Game.standardSize, Color.WHITE);
        CyaLocation destination = object.travel();
        if (destination != null) {
            Terminal.blankLine();
        }
        return new Outcome(destination, false);
    }

    /**
     * ENTER first opens something in the room, and otherwise walks to a
     * connected area. That makes ENTER DOOR, ENTER SHACK and ENTER UPSTAIRS
     * all do what a player expects without a separate set of verbs.
     */
    private Exit enter(String target, String verb) throws InterruptedException {
        if (target.isEmpty()) {
            printTargetUsage(verb, null);
            return null;
        }
        Resolution room = resolveRoom(target);
        if (room.object() == null && !room.ambiguous()) {
            CyaLocation named = CyaLocation.fromName(target);
            if (named != null && CyaLocation.neighbours(here).contains(named)) {
                CyaLocation destination = go(target);
                return destination == null ? null : new Exit(destination, false);
            }
        }
        Outcome outcome = interact(target, verb);
        if (outcome.finished()) {
            return new Exit(null, false);
        }
        if (outcome.destination() != null) {
            return new Exit(outcome.destination(), false);
        }
        return null;
    }

    /** The first thing in this area a carried tool can be applied to. */
    private Interactable toolTarget(Interactable tool) {
        for (Interactable object : objects) {
            if (!object.isHidden() && tool.canUseOn(object.id)) {
                return object;
            }
        }
        return null;
    }

    /** Splits "tool on thing" into its two halves, or null when it is not that shape. */
    private static String[] splitOn(String target) {
        int at = target.toLowerCase().indexOf(" on ");
        if (at < 0) {
            return null;
        }
        String tool = target.substring(0, at).trim();
        String thing = target.substring(at + 4).trim();
        return tool.isEmpty() || thing.isEmpty() ? null : new String[] {tool, thing};
    }

    private static void printNothingToUse(Interactable tool) {
        Terminal.print("you have no use for the " + tool.displayName + " here.",
            15, Game.standardSize, Color.RED);
    }

    private static void printNoEffect(Interactable tool, Interactable thing) {
        Terminal.print("the " + tool.displayName + " is no help with the " + thing.displayName + ".",
            15, Game.standardSize, Color.RED);
    }

    private void take(String target) {
        if (target.isEmpty()) {
            printTargetUsage("take", null);
            return;
        }
        Resolution room = resolveRoom(target);
        if (room.ambiguous()) {
            printAmbiguous(room.candidates());
            return;
        }
        Interactable object = room.object();
        if (object == null) {
            Resolution carried = resolveCarried(target);
            if (carried.ambiguous()) {
                printAmbiguous(carried.candidates());
                return;
            }
            if (carried.object() != null) {
                Terminal.print(carried.object().highlightNames("you already have the "
                    + carried.object().displayName + "."),
                    15, Game.standardSize, Color.WHITE);
                return;
            }
            printTargetUsage("take", target);
            return;
        }
        if (SaveState.hasItem(object.id)) {
            Terminal.print(object.highlightNames("you already have the "
                + object.displayName + "."), 15, Game.standardSize, Color.WHITE);
            return;
        }
        Terminal.print(object.takeSpans(), 15, Game.standardSize, Color.WHITE);
        if (!object.takeable) {
            return;
        }
        SaveState.addItem(object.id);
        if (object.onTake != null) {
            object.onTake.run();
        }
    }

    /** GO BACK, and its return and home siblings, ask for the way in. */
    private static boolean isBackTarget(String target) {
        String name = target.trim();
        if (name.toLowerCase().startsWith("to ")) {
            name = name.substring(3).trim();
        }
        return Game.isBackCommand(name);
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

        // The house only opens once the cellar puzzle has been solved.
        if (destination == CyaLocation.HOUSE && !SaveState.cellarPuzzleSolved) {
            String backTo = here == CyaLocation.SHACK_OUTER ? "the shack" : "the cemetery";
            Terminal.print("you try the doors of the house. every one of them is locked,"
                + " and you can't find an unlocked door. You head back to " + backTo
                + ", and somewhere beneath the ground you can hear the faint hum of electronics",
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

    /** Puts down a spent tool. Items still needed refuse to be dropped. */
    private void drop(String target) {
        if (target.isEmpty()) {
            Terminal.print("drop what? use DROP <thing>, or INVENTORY to see what you carry.",
                15, Game.standardSize, Color.RED);
            return;
        }
        Resolution carried = resolveCarried(target);
        if (carried.ambiguous()) {
            printAmbiguous(carried.candidates());
            return;
        }
        Interactable object = carried.object();
        if (object == null) {
            Terminal.print("you aren't carrying \"" + target + "\".",
                15, Game.standardSize, Color.RED);
            return;
        }
        if (!object.canDrop()) {
            Terminal.print("you should keep the " + object.displayName + " for now.",
                15, Game.standardSize, Color.RED);
            return;
        }
        SaveState.dropItem(object.id);
        Terminal.print(object.highlightNames("you set down the " + object.displayName + "."),
            15, Game.standardSize, Color.WHITE);
    }

    /** A short journal of the clues found so far, so a long gap does not lose them. */
    private void showNotes() {
        Terminal.print("NOTES -----------", 15, Game.standardSize, Color.GREEN);
        boolean any = false;
        if (SaveState.purpose != null) {
            Terminal.print("you are looking for: " + SaveState.purpose,
                15, Game.standardSize, Color.WHITE);
            any = true;
        }
        if (SaveState.hasItem("crumpledPaper") || SaveState.spokenToMysteriousMan) {
            Terminal.print("- the man's note: the plate was taken before the boards, and the grave "
                + "was never first.", 15, Game.standardSize, Color.WHITE);
            any = true;
        }
        if (SaveState.readTableClue) {
            Terminal.print("- the table: the boards were the last thing he shut.",
                15, Game.standardSize, Color.WHITE);
            any = true;
        }
        if (SaveState.cellarPuzzleSolved) {
            Terminal.print("- the cellar: the pipe is open, and the house is awake.",
                15, Game.standardSize, Color.WHITE);
            any = true;
        } else if (SaveState.valvesOpened > 0) {
            Terminal.print("- the cellar: " + SaveState.valvesOpened
                + " valves are open.", 15, Game.standardSize, Color.WHITE);
            any = true;
        }
        if (!any) {
            Terminal.print("nothing worth writing down yet.", 15, Game.standardSize, Color.GRAY);
        }
    }

    private void showInventory() {
        if (SaveState.takenObjects.isEmpty()) {
            Terminal.print("your inventory is empty.", 15, Game.standardSize, Color.GRAY);
            return;
        }
        for (String id : SaveState.takenObjects) {
            Interactable object = ObjectCatalog.byId(id);
            List<TextSpan> line = object == null
                ? TextSpan.highlighting("- " + id, id, NAME)
                : object.highlightNames("- " + object.displayName);
            Terminal.print(line, 15, Game.standardSize, Color.WHITE);
        }
    }

    private void printHelp() {
        Terminal.print(TextSpan.highlighting("COMMANDS -----------", "COMMANDS", Color.WHITE),
            15, Game.standardSize, Color.GREEN);
        printCommand("look", "describe the area you are in");
        printCommand("look [at] <thing>", "examine something here or carried");
        printCommand("interact <thing>", "talk to, open, climb or use something");
        printCommand("talk | use | turn", "aliases for interact <thing>");
        printCommand("open | climb | pull", "aliases for interact <thing>");
        printCommand("use <tool> on <thing>", "apply a carried tool to something");
        printCommand("take <thing>", "pick something up");
        printCommand("drop <thing>", "put down a spent tool");
        printCommand("inventory", "show what you are carrying");
        printCommand("notes | journal", "review the clues you have found");
        printCommand("go", "list where you can go");
        printCommand("go [to] <area>", "travel to a connected area");
        printCommand("go back", "return to the previous area");
        printCommand("help", "show this command list");
        printCommand("leave", "return to the previous area");
        printCommand("back | return | home", "aliases for leave");
        printCommand("menu", "save and return to the boot menu");
        printCommand("quit", "quit the game (with confirmation)");
        Terminal.divider();
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

    private record Resolution(Interactable object, List<Interactable> candidates) {
        boolean ambiguous() {
            return object == null && !candidates.isEmpty();
        }
    }

    private static final Resolution NONE = new Resolution(null, List.of());

    private Resolution resolveRoom(String name) {
        return resolve(objects, name, true);
    }

    private Resolution resolveCarried(String name) {
        List<Interactable> carried = new ArrayList<>();
        for (String id : SaveState.takenObjects) {
            Interactable object = ObjectCatalog.byId(id);
            if (object != null) {
                carried.add(object);
            }
        }
        return resolve(carried, name, false);
    }

    /**
     * Finds an object by an exact name first, then by a unique word prefix.
     * Several matches come back as candidates so the caller can ask which one.
     */
    private static Resolution resolve(List<Interactable> pool, String name, boolean skipHidden) {
        String target = normalizeTarget(name);
        if (target.isEmpty()) {
            return NONE;
        }

        List<Interactable> exact = new ArrayList<>();
        for (Interactable object : pool) {
            if ((!skipHidden || !object.isHidden()) && object.matches(target)) {
                exact.add(object);
            }
        }
        if (exact.size() == 1) {
            return new Resolution(exact.get(0), List.of());
        }
        if (exact.size() > 1) {
            return new Resolution(null, List.copyOf(exact));
        }

        if (target.length() < 2) {
            return NONE;
        }
        List<Interactable> partial = new ArrayList<>();
        for (Interactable object : pool) {
            if ((!skipHidden || !object.isHidden()) && object.matchesPartially(target)) {
                partial.add(object);
            }
        }
        if (partial.size() == 1) {
            return new Resolution(partial.get(0), List.of());
        }
        if (partial.size() > 1) {
            return new Resolution(null, List.copyOf(partial));
        }
        return NONE;
    }

    /** Lists the objects a partial name matched, so the player can be specific. */
    private void printAmbiguous(List<Interactable> candidates) {
        Terminal.print("which one do you mean?", 15, Game.standardSize, Color.RED);
        int shown = 0;
        for (Interactable object : candidates) {
            if (shown == 6) {
                Terminal.print("... and " + (candidates.size() - shown) + " more.",
                    15, Game.standardSize, Color.GRAY);
                break;
            }
            Terminal.print("- " + object.displayName, 15, Game.standardSize, Color.WHITE);
            shown++;
        }
    }

    /** A typed name with the leading "the" dropped, as the aliases are stored. */
    private static String normalizeTarget(String name) {
        String target = name.trim();
        if (target.toLowerCase().startsWith("the ")) {
            target = target.substring(4).trim();
        }
        return target;
    }
}
